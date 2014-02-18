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
package org.elasticsearch.indices.analyze;

import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.ElasticsearchIllegalArgumentException;
import org.elasticsearch.action.admin.indices.analyze.AnalyzeRequestBuilder;
import org.elasticsearch.action.admin.indices.analyze.AnalyzeResponse;
import org.elasticsearch.common.Priority;
import org.elasticsearch.common.inject.Injector;
import org.elasticsearch.common.inject.ModulesBuilder;
import org.elasticsearch.common.settings.ImmutableSettings;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.settings.SettingsModule;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.elasticsearch.env.Environment;
import org.elasticsearch.env.EnvironmentModule;
import org.elasticsearch.index.Index;
import org.elasticsearch.index.IndexNameModule;
import org.elasticsearch.index.analysis.AnalysisModule;
import org.elasticsearch.index.analysis.AnalysisService;
import org.elasticsearch.index.settings.IndexSettingsModule;
import org.elasticsearch.indices.analysis.IndicesAnalysisModule;
import org.elasticsearch.indices.analysis.IndicesAnalysisService;
import org.elasticsearch.test.ElasticsearchIntegrationTest;
import org.junit.Test;

import java.io.IOException;

import static org.elasticsearch.common.settings.ImmutableSettings.settingsBuilder;
import static org.hamcrest.Matchers.equalTo;

/**
 *
 */
public class AnalyzeActionTests extends ElasticsearchIntegrationTest {
    
    @Test
    public void simpleAnalyzerTests() throws Exception {
        try {
            client().admin().indices().prepareDelete("test").execute().actionGet();
        } catch (Exception e) {
            // ignore
        }

        createIndex("test");
        client().admin().cluster().prepareHealth().setWaitForEvents(Priority.LANGUID).setWaitForGreenStatus().execute().actionGet();

        for (int i = 0; i < 10; i++) {
            AnalyzeResponse analyzeResponse = client().admin().indices().prepareAnalyze("test", "this is a test").execute().actionGet();
            assertThat(analyzeResponse.getTokens().size(), equalTo(4));
            AnalyzeResponse.AnalyzeToken token = analyzeResponse.getTokens().get(0);
            assertThat(token.getTerm(), equalTo("this"));
            assertThat(token.getStartOffset(), equalTo(0));
            assertThat(token.getEndOffset(), equalTo(4));
            token = analyzeResponse.getTokens().get(1);
            assertThat(token.getTerm(), equalTo("is"));
            assertThat(token.getStartOffset(), equalTo(5));
            assertThat(token.getEndOffset(), equalTo(7));
            token = analyzeResponse.getTokens().get(2);
            assertThat(token.getTerm(), equalTo("a"));
            assertThat(token.getStartOffset(), equalTo(8));
            assertThat(token.getEndOffset(), equalTo(9));
            token = analyzeResponse.getTokens().get(3);
            assertThat(token.getTerm(), equalTo("test"));
            assertThat(token.getStartOffset(), equalTo(10));
            assertThat(token.getEndOffset(), equalTo(14));
        }
    }
    
    @Test
    public void analyzeNumericField() throws ElasticsearchException, IOException {
        try {
            client().admin().indices().prepareDelete("test").execute().actionGet();
        } catch (Exception e) {
            // ignore
        }
        createIndex("test");
        
        client().admin().cluster().prepareHealth().setWaitForEvents(Priority.LANGUID).setWaitForGreenStatus().execute().actionGet();
        client().prepareIndex("test", "test", "1")
        .setSource(XContentFactory.jsonBuilder()
                .startObject()
                    .field("long", 1l)
                    .field("double", 1.0d)
                .endObject())
        .setRefresh(true).execute().actionGet();

        try {
            client().admin().indices().prepareAnalyze("test", "123").setField("long").execute().actionGet();
        } catch (ElasticsearchIllegalArgumentException ex) {
        }
        try {
            client().admin().indices().prepareAnalyze("test", "123.0").setField("double").execute().actionGet();
        } catch (ElasticsearchIllegalArgumentException ex) {
        }
    }

    @Test
    public void analyzeWithNoIndex() throws Exception {

        AnalyzeResponse analyzeResponse = client().admin().indices().prepareAnalyze("THIS IS A TEST").setAnalyzer("simple").execute().actionGet();
        assertThat(analyzeResponse.getTokens().size(), equalTo(4));

        analyzeResponse = client().admin().indices().prepareAnalyze("THIS IS A TEST").setTokenizer("keyword").setTokenFilters("lowercase").execute().actionGet();
        assertThat(analyzeResponse.getTokens().size(), equalTo(1));
        assertThat(analyzeResponse.getTokens().get(0).getTerm(), equalTo("this is a test"));
    }

    @Test
    public void analyzeWithCharFilter() throws Exception {

        ImmutableSettings.Builder settings = settingsBuilder()
                .put("index.analysis.char_filter.my_mapping.type", "mapping")
                .putArray("index.analysis.char_filter.my_mapping.mappings", "ph=>f", "qu=>q")
                .put("index.analysis.analyzer.custom_with_char_filter.tokenizer", "standard")
                .putArray("index.analysis.analyzer.custom_with_char_filter.char_filter", "my_mapping");

        prepareCreate("test", 1, settings).execute().actionGet();
        client().admin().cluster().prepareHealth().setWaitForEvents(Priority.LANGUID).setWaitForGreenStatus().execute().actionGet();

        AnalyzeResponse analyzeResponse = client().admin().indices().prepareAnalyze("test", "<h2><b>THIS</b> IS A</h2> <a href=\"#\">TEST</a>").setTokenizer("standard").setCharFilters("html_strip").execute().actionGet();
        assertThat(analyzeResponse.getTokens().size(), equalTo(4));

        analyzeResponse = client().admin().indices().prepareAnalyze("THIS IS A <b>TEST</b>").setTokenizer("keyword").setTokenFilters("lowercase").setCharFilters("html_strip").execute().actionGet();
        assertThat(analyzeResponse.getTokens().size(), equalTo(1));
        assertThat(analyzeResponse.getTokens().get(0).getTerm(), equalTo("this is a test"));

        analyzeResponse = client().admin().indices().prepareAnalyze("test", "jeff quit phish").setTokenizer("keyword").setTokenFilters("lowercase").setCharFilters("my_mapping").execute().actionGet();
        assertThat(analyzeResponse.getTokens().size(), equalTo(1));
        assertThat(analyzeResponse.getTokens().get(0).getTerm(), equalTo("jeff qit fish"));
    }

    @Test
    public void analyzerWithFieldOrTypeTests() throws Exception {

        createIndex("test");
        client().admin().cluster().prepareHealth().setWaitForEvents(Priority.LANGUID).setWaitForGreenStatus().execute().actionGet();

        client().admin().indices().preparePutMapping("test")
                .setType("document").setSource(
                "{\n" +
                        "    \"document\":{\n" +
                        "        \"properties\":{\n" +
                        "            \"simple\":{\n" +
                        "                \"type\":\"string\",\n" +
                        "                \"analyzer\": \"simple\"\n" +
                        "            }\n" +
                        "        }\n" +
                        "    }\n" +
                        "}"
        ).execute().actionGet();

        for (int i = 0; i < 10; i++) {
            final AnalyzeRequestBuilder requestBuilder = client().admin().indices().prepareAnalyze("test", "THIS IS A TEST");
            requestBuilder.setField("document.simple");
            AnalyzeResponse analyzeResponse = requestBuilder.execute().actionGet();
            assertThat(analyzeResponse.getTokens().size(), equalTo(4));
            AnalyzeResponse.AnalyzeToken token = analyzeResponse.getTokens().get(3);
            assertThat(token.getTerm(), equalTo("test"));
            assertThat(token.getStartOffset(), equalTo(10));
            assertThat(token.getEndOffset(), equalTo(14));
        }
    }
}
