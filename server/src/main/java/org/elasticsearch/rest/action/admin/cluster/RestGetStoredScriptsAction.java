/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.elasticsearch.rest.action.admin.cluster;

import org.elasticsearch.action.admin.cluster.storedscripts.GetStoredScriptsRequest;
import org.elasticsearch.client.node.NodeClient;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.rest.BaseRestHandler;
import org.elasticsearch.rest.RestController;
import org.elasticsearch.rest.RestRequest;
import org.elasticsearch.rest.action.RestStatusToXContentListener;

import java.io.IOException;

import static org.elasticsearch.rest.RestRequest.Method.GET;

public class RestGetStoredScriptsAction extends BaseRestHandler {

    public RestGetStoredScriptsAction(Settings settings, RestController controller) {
        super(settings);

        controller.registerHandler(GET, "/_scripts", this);
    }

    @Override
    public String getName() {
        return "get_all_stored_scripts_action";
    }

    @Override
    public RestChannelConsumer prepareRequest(final RestRequest request, NodeClient client) throws IOException {
        GetStoredScriptsRequest getRequest = new GetStoredScriptsRequest();
        getRequest.masterNodeTimeout(request.paramAsTime("master_timeout", getRequest.masterNodeTimeout()));

//        final boolean implicitAll = getRequest.names().length == 0;

        return channel -> client.admin().cluster().getStoredScripts(getRequest, new RestStatusToXContentListener<>(channel)
//            {
//            @Override
//            protected RestStatus getStatus(final GetStoredScriptsResponse response)
//            {
//                Map<String, StoredScriptSource> storedScripts = response.getStoredScripts();
//                final boolean templateExists = storedScripts != null && !storedScripts.isEmpty();
//                return (templateExists || implicitAll) ? OK : NOT_FOUND;
//            }
//        }
        );
    }
}
