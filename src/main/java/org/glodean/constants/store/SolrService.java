package org.glodean.constants.store;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.solr.client.solrj.impl.HttpSolrClientBase;
import org.apache.solr.client.solrj.request.UpdateRequest;
import org.apache.solr.common.SolrInputDocument;
import org.glodean.constants.model.ClassConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public record SolrService(@Autowired HttpSolrClientBase solrClient) {
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
                parentDocument.setField("doc_type", "child");
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
}
