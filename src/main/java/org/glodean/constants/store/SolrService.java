package org.glodean.constants.store;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.solr.client.solrj.impl.HttpSolrClientBase;
import org.apache.solr.client.solrj.request.QueryRequest;
import org.apache.solr.client.solrj.request.UpdateRequest;
import org.apache.solr.common.SolrInputDocument;
import org.apache.solr.common.params.ModifiableSolrParams;
import org.apache.solr.common.util.NamedList;
import org.glodean.constants.dto.GetClassConstantsReply;
import org.glodean.constants.dto.GetClassConstantsRequest;
import org.glodean.constants.model.ClassConstant;
import org.glodean.constants.model.ClassConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.support.atomic.RedisAtomicInteger;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.*;

@Service
public class SolrService {
    private static final Logger logger = LogManager.getLogger(SolrService.class);

    private final HttpSolrClientBase solrClient;
    private final RedisConnectionFactory connectionFactory;

    public SolrService(@Autowired HttpSolrClientBase solrClient,
                       @Autowired RedisConnectionFactory connectionFactory) {
        this.solrClient = solrClient;
        this.connectionFactory = connectionFactory;
    }

    public Mono<ClassConstants> store(ClassConstants constants, String project) {
        int latestVersion = new RedisAtomicInteger("IdCounter:" + project + ":" + constants.name(), connectionFactory).incrementAndGet();
        return this.store(constants, project, latestVersion);
    }

    public Mono<ClassConstants> store(ClassConstants constants, String project, int version) {
        logger.atInfo().log("Storing the constants {}", constants);
        SolrInputDocument parentDocument = new SolrInputDocument();
        var id = project + ":" + constants.name() + ":" + version;
        parentDocument.setField("id", id);
        parentDocument.setField("project", project);
        parentDocument.setField("doc_type", "parent");
        parentDocument.setField("class_name", constants.name());
        parentDocument.setField("class_version", version);
        int index = 0;
        for (var constant : constants.constants()) {
            var value = constant.value().toString();
            for (var usage : constant.constantData()) {
                SolrInputDocument childDocument = new SolrInputDocument();
                childDocument.setField("doc_type", "child");
                childDocument.setField("constant_value_s", value);
                childDocument.setField("usage_type_s", usage.name());
                childDocument.setField("id", id + ":" + index);
                childDocument.setField("_root_", id);
                parentDocument.addChildDocument(childDocument);
                index++;
            }
        }
        var request = new UpdateRequest();
        request.add(parentDocument);
        request.setParam("commit", "true");

        return Mono.fromFuture(solrClient.requestAsync(request, "Constants"))
                .map(_ -> constants);
    }

    public /*@Cacheable(cacheNames = "Constants", key = "#request.key()")*/
    Mono<GetClassConstantsReply> find(GetClassConstantsRequest request) {
        ModifiableSolrParams params = new ModifiableSolrParams();
        params.set("q", "*:*");
        params.set("fq", "_root_:\"" + request.key() + "\"");
        params.set("fl", "constant_value_s, usage_type_s");
        params.set("rows", 1000);
        QueryRequest query = new QueryRequest(params);
        query.setPath("/select");
        logger.atInfo().log("Search query {}", query.getParams());
        return Mono.fromFuture(solrClient.requestAsync(query, "Constants"))
                .map(SolrService::apply);
    }

    private static GetClassConstantsReply apply(NamedList<Object> list) {
        Map<Object, Collection<ClassConstant.UsageType>> constants = HashMap.newHashMap(10);
        if (list.get("response") instanceof Collection<?> response) {
            for (var responsePart : response)
                if (responsePart instanceof Map<?, ?> reMap && !reMap.isEmpty()) {
                    logger.atInfo().log("received {}", reMap);
                    String value = (String) reMap.get("constant_value_s");
                    ClassConstant.UsageType usage = ClassConstant.UsageType.valueOf((String) reMap.get("usage_type_s"));
                    constants.computeIfAbsent(value, _ -> EnumSet.noneOf(ClassConstant.UsageType.class))
                            .add(usage);
                }
        }
        return new GetClassConstantsReply(constants);
    }
}
