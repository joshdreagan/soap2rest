/*
 * Copyright (C) Red Hat, Inc.
 * http://www.redhat.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.examples.soap2rest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.ws.rs.core.MediaType;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.apache.camel.processor.aggregate.AggregationStrategy;
import org.apache.cxf.Bus;
import org.apache.cxf.bus.spring.SpringBus;
import org.apache.cxf.feature.Feature;
import org.apache.cxf.transport.common.gzip.GZIPFeature;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

@Component
public class CamelRouteConfiguration extends RouteBuilder {

  @Override
  public void configure() {
    from("cxf:{{application.soap.bindAddress}}?serviceClass=org.apache.camel.examples.soap2rest.Order&wsdlURL=/META-INF/wsdl/OrderService.wsdl&dataFormat=PAYLOAD")
      .routeId("soapConsumerRoute")
      .to("xslt:/META-INF/xslt/xml-to-json.xsl")
      .unmarshal().json(JsonLibrary.Jackson, Map.class)
      .enrich("direct:enrichOrder", orderEnrichmentStrategy())
      .marshal().json(JsonLibrary.Jackson)
      .convertBodyTo(String.class)
      .setHeader(Exchange.HTTP_METHOD, constant("POST"))
      .setHeader(Exchange.CONTENT_TYPE, constant(MediaType.APPLICATION_JSON))
      .setHeader(Exchange.ACCEPT_CONTENT_TYPE, constant(MediaType.APPLICATION_JSON))
      .to("cxfrs:{{application.rest.address}}?httpClientAPI=true")
      .unmarshal().json(JsonLibrary.Jackson, Map.class)
      .transform().groovy("return new org.apache.camel.examples.soap2rest.PlaceOrderResponseType(orderNumber: request.body['orderNumber']);")
      .marshal().jaxb("org.apache.camel.examples.soap2rest")
    ;
    
    from("direct:enrichOrder")
      .routeId("orderEnrichmentRoute")
      .multicast(sqlResultsAggregationStrategy(), true)
        .to("direct:enrichCustomer")
        .to("direct:enrichItem")
      .end()
    ;
    
    from("direct:enrichCustomer")
      .routeId("customerEnrichmentRoute")
      .setHeader("Soap2RestEnrichmentType", constant("CUSTOMER"))
      .setBody().groovy("request.body['placeOrder']['customerId']")
      .to("sql:select * from {{application.sql.customersTable}} where ID=:#${body}?dataSource=#dataSource&outputType=SelectOne")
    ;
    
    from("direct:enrichItem")
      .routeId("itemEnrichmentRoute")
      .setHeader("Soap2RestEnrichmentType", constant("ITEM"))
      .setBody().groovy("request.body['placeOrder']['itemId']")
      .to("sql:select * from {{application.sql.itemsTable}} where ID=:#${body}?dataSource=#dataSource&outputType=SelectOne")
    ;
  }
  
  @Bean
  Bus cxf() {
    Bus bus = new SpringBus();
    bus.setId("cxf");
    List<Feature> features = new ArrayList<>();
    features.add(new GZIPFeature());
    bus.setFeatures(features);
    return bus;
  }
  
  @Bean
  AggregationStrategy orderEnrichmentStrategy() {
    return new AggregationStrategy() {
      @Override
      public Exchange aggregate(Exchange original, Exchange resource) {
        Map<String, Object> jsonRequest = original.getIn().getBody(Map.class);
        Map<String, Object> enrichmentResults = resource.getIn().getBody(Map.class);
        ((Map<String, Object>) jsonRequest.get("placeOrder")).remove("customerId");
        ((Map<String, Object>) jsonRequest.get("placeOrder")).remove("itemId");
        ((Map<String, Object>) jsonRequest.get("placeOrder")).putAll(enrichmentResults);
        return original;
      }
    };
  }
  
  @Bean
  AggregationStrategy sqlResultsAggregationStrategy() {
    return new AggregationStrategy() {
      @Override
      public Exchange aggregate(Exchange oldExchange, Exchange newExchange) {
        Map<String, Object> sqlResults = newExchange.getIn().getBody(Map.class);
        if (oldExchange == null) {
          oldExchange = newExchange;
          oldExchange.getIn().setBody(new HashMap<>());
        }
        String enrichmentType = newExchange.getIn().getHeader("Soap2RestEnrichmentType", String.class);
        switch (enrichmentType) {
          case "CUSTOMER":
            Map<String, Object> customer = new HashMap<>();
            customer.put("customerId", (String) sqlResults.get("ID"));
            customer.put("firstName", (String) sqlResults.get("FIRST_NAME"));
            customer.put("lastName", (String) sqlResults.get("LAST_NAME"));
            customer.put("address", (String) sqlResults.get("FIRST_NAME"));
            oldExchange.getIn().getBody(Map.class).put("customer", customer);
            break;
          case "ITEM":
            Map<String, Object> item = new HashMap<>();
            item.put("itemId", (String) sqlResults.get("ID"));
            item.put("name", (String) sqlResults.get("ITEM_NAME"));
            item.put("description", (String) sqlResults.get("DESCRIPTION"));
            oldExchange.getIn().getBody(Map.class).put("item", item);
            break;
        }
        return oldExchange;
      }
    };
  }
}
