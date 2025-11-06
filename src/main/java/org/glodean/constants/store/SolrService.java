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
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.*;

@Service
public record SolrService(@Autowired HttpSolrClientBase solrClient,
                          @Autowired CacheManager cacheManager) {
    private static final Logger logger = LogManager.getLogger(SolrService.class);

    public Mono<ClassConstants> store(ClassConstants constants, String project, int version) {
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
        request.setCommitWithin(10_000);

        return Mono.fromFuture(solrClient.requestAsync(request, "Constants"))
                .map(_ -> constants);
    }

    public Mono<GetClassConstantsReply> find(GetClassConstantsRequest request) {
        Cache constants = cacheManager.getCache("constants");
        if (constants != null) {
            GetClassConstantsReply reply = constants.get(request, GetClassConstantsReply.class);
            if (reply != null) {
                return Mono.just(reply);
            }
            return findDirect(request).map(
                    r -> {
                        constants.put(request, r);
                        return r;
                    });
        }
        return findDirect(request);
    }

    private Mono<GetClassConstantsReply> findDirect(GetClassConstantsRequest request) {
        ModifiableSolrParams params = new ModifiableSolrParams();
        params.set("q", "*:*");
        params.set("q.op", "AND");
        params.set("fq", "_root_:\"" + request.project() + ":" + request.clazz() + ":" + request.version() + ":*\"");
        params.set("fq", "doc_type:\"child\"");
        params.set("fl", "constant_value_s, usage_type_s");
        params.set("rows", 1000);
        QueryRequest query = new QueryRequest(params);
        query.setPath("/select");
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

    @Autowired
    public HttpSolrClientBase solrClient() {
        return solrClient;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        var that = (SolrService) obj;
        return Objects.equals(this.solrClient, that.solrClient);
    }

    @Override
    public int hashCode() {
        return Objects.hash(solrClient);
    }

    @Override
    public String toString() {
        return "SolrService[" +
                "solrClient=" + solrClient + ']';
    }

}
