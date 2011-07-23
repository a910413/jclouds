/**
 *
 * Copyright (C) 2011 Cloud Conscious, LLC. <info@cloudconscious.com>
 *
 * ====================================================================
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ====================================================================
 */
package org.jclouds.vcloud;

import static org.jclouds.Constants.PROPERTY_SESSION_INTERVAL;
import static org.jclouds.vcloud.options.InstantiateVAppTemplateOptions.Builder.processorCount;
import static org.testng.Assert.assertEquals;

import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URI;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicReference;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.jclouds.http.HttpRequest;
import org.jclouds.http.RequiresHttp;
import org.jclouds.http.functions.ParseSax;
import org.jclouds.http.functions.ReleasePayloadAndReturn;
import org.jclouds.rest.AuthorizationException;
import org.jclouds.rest.ConfiguresRestClient;
import org.jclouds.rest.RestClientTest;
import org.jclouds.rest.RestContextFactory;
import org.jclouds.rest.RestContextSpec;
import org.jclouds.rest.functions.ReturnNullOnNotFoundOr404;
import org.jclouds.rest.internal.RestAnnotationProcessor;
import org.jclouds.util.Strings2;
import org.jclouds.vcloud.config.VCloudExpressRestClientModule;
import org.jclouds.vcloud.domain.AllocationModel;
import org.jclouds.vcloud.domain.Org;
import org.jclouds.vcloud.domain.ReferenceType;
import org.jclouds.vcloud.domain.Task;
import org.jclouds.vcloud.domain.VCloudSession;
import org.jclouds.vcloud.domain.VDC;
import org.jclouds.vcloud.domain.VDCStatus;
import org.jclouds.vcloud.domain.internal.CatalogImpl;
import org.jclouds.vcloud.domain.internal.CatalogItemImpl;
import org.jclouds.vcloud.domain.internal.OrgImpl;
import org.jclouds.vcloud.domain.internal.ReferenceTypeImpl;
import org.jclouds.vcloud.domain.internal.VDCImpl;
import org.jclouds.vcloud.domain.network.FenceMode;
import org.jclouds.vcloud.domain.network.NetworkConfig;
import org.jclouds.vcloud.filters.SetVCloudTokenCookie;
import org.jclouds.vcloud.functions.ParseTaskFromLocationHeader;
import org.jclouds.vcloud.options.CloneVAppOptions;
import org.jclouds.vcloud.options.InstantiateVAppTemplateOptions;
import org.jclouds.vcloud.xml.CatalogItemHandler;
import org.jclouds.vcloud.xml.OrgHandler;
import org.jclouds.vcloud.xml.OrgNetworkFromVCloudExpressNetworkHandler;
import org.jclouds.vcloud.xml.TaskHandler;
import org.jclouds.vcloud.xml.TasksListHandler;
import org.jclouds.vcloud.xml.VCloudExpressCatalogHandler;
import org.jclouds.vcloud.xml.VCloudExpressVAppHandler;
import org.jclouds.vcloud.xml.VCloudExpressVAppTemplateHandler;
import org.jclouds.vcloud.xml.VDCHandler;
import org.testng.annotations.Test;

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.inject.AbstractModule;
import com.google.inject.Module;
import com.google.inject.TypeLiteral;

/**
 * Tests behavior of {@code VCloudExpressAsyncClient}
 * 
 * @author Adrian Cole
 */
// NOTE:without testName, this will not call @Before* and fail w/NPE during
// surefire
@Test(groups = "unit", testName = "VCloudExpressAsyncClientTest")
public class VCloudExpressAsyncClientTest extends RestClientTest<VCloudExpressAsyncClient> {

   public void testListOrgs() {
      assertEquals(injector.getInstance(VCloudExpressAsyncClient.class).listOrgs().toString(),
            ImmutableMap.of(ORG_REF.getName(), ORG_REF).toString());
   }

   public void testInstantiateVAppTemplateInVDCURI() throws SecurityException, NoSuchMethodException, IOException {
      Method method = VCloudExpressAsyncClient.class.getMethod("instantiateVAppTemplateInVDC", URI.class, URI.class,
            String.class, InstantiateVAppTemplateOptions[].class);
      HttpRequest request = processor.createRequest(method,
            URI.create("https://vcloud.safesecureweb.com/api/v0.8/vdc/1"),
            URI.create("https://vcloud.safesecureweb.com/api/v0.8/vAppTemplate/3"), "my-vapp");

      assertRequestLineEquals(request,
            "POST https://vcloud.safesecureweb.com/api/v0.8/vdc/1/action/instantiateVAppTemplate HTTP/1.1");
      assertNonPayloadHeadersEqual(request, "Accept: application/vnd.vmware.vcloud.vApp+xml\n");
      assertPayloadEquals(request, Strings2.toStringAndClose(getClass().getResourceAsStream("/newvapp-hosting.xml")),
            "application/vnd.vmware.vcloud.instantiateVAppTemplateParams+xml", false);

      assertResponseParserClassEquals(method, request, ParseSax.class);
      assertSaxResponseParserClassEquals(method, VCloudExpressVAppHandler.class);
      assertExceptionParserClassEquals(method, null);

      checkFilters(request);
   }

   public void testInstantiateVAppTemplateInVDCURIOptions() throws SecurityException, NoSuchMethodException,
         IOException {
      Method method = VCloudExpressAsyncClient.class.getMethod("instantiateVAppTemplateInVDC", URI.class, URI.class,
            String.class, InstantiateVAppTemplateOptions[].class);
      HttpRequest request = processor.createRequest(
            method,
            URI.create("https://vcloud.safesecureweb.com/api/v0.8/vdc/1"),
            URI.create("https://vcloud.safesecureweb.com/api/v0.8/vAppTemplate/3"),
            "my-vapp",
            processorCount(1)
                  .memory(512)
                  .disk(1024)
                  .addNetworkConfig(
                        new NetworkConfig(null, URI.create("https://vcloud.safesecureweb.com/network/1990"),
                              FenceMode.BRIDGED)));

      assertRequestLineEquals(request,
            "POST https://vcloud.safesecureweb.com/api/v0.8/vdc/1/action/instantiateVAppTemplate HTTP/1.1");
      assertNonPayloadHeadersEqual(request, "Accept: application/vnd.vmware.vcloud.vApp+xml\n");
      assertPayloadEquals(request,
            Strings2.toStringAndClose(getClass().getResourceAsStream("/newvapp-hostingcpumemdisk.xml")),
            "application/vnd.vmware.vcloud.instantiateVAppTemplateParams+xml", false);

      assertResponseParserClassEquals(method, request, ParseSax.class);
      assertSaxResponseParserClassEquals(method, VCloudExpressVAppHandler.class);
      assertExceptionParserClassEquals(method, null);

      checkFilters(request);
   }

   @Test(expectedExceptions = IllegalArgumentException.class)
   public void testInstantiateVAppTemplateInOrgOptionsIllegalName() throws SecurityException, NoSuchMethodException,
         IOException {
      Method method = VCloudExpressAsyncClient.class.getMethod("instantiateVAppTemplateInVDC", URI.class, URI.class,
            String.class, InstantiateVAppTemplateOptions[].class);
      processor.createRequest(
            method,
            URI.create("https://vcloud.safesecureweb.com/api/v0.8/vdc/1"),
            URI.create("https://vcloud.safesecureweb.com/api/v0.8/vdc/1"),
            "CentOS 01",
            processorCount(1)
                  .memory(512)
                  .disk(1024)
                  .addNetworkConfig(
                        new NetworkConfig("aloha", URI
                              .create("https://vcenterprise.bluelock.com/api/v1.0/network/1990"), null)));
   }

   public void testCloneVAppInVDC() throws SecurityException, NoSuchMethodException, IOException {
      Method method = VCloudExpressAsyncClient.class.getMethod("cloneVAppInVDC", URI.class, URI.class, String.class,
            CloneVAppOptions[].class);
      HttpRequest request = processor.createRequest(method,
            URI.create("https://vcloud.safesecureweb.com/api/v0.8/vdc/1"),
            URI.create("https://vcloud.safesecureweb.com/api/v0.8/vapp/4181"), "my-vapp");

      assertRequestLineEquals(request, "POST https://vcloud.safesecureweb.com/api/v0.8/vdc/1/action/cloneVApp HTTP/1.1");
      assertNonPayloadHeadersEqual(request, "Accept: application/vnd.vmware.vcloud.task+xml\n");
      assertPayloadEquals(request, Strings2.toStringAndClose(getClass().getResourceAsStream("/cloneVApp-default.xml")),
            "application/vnd.vmware.vcloud.cloneVAppParams+xml", false);

      assertResponseParserClassEquals(method, request, ParseSax.class);
      assertSaxResponseParserClassEquals(method, TaskHandler.class);
      assertExceptionParserClassEquals(method, null);

      checkFilters(request);
   }

   public void testCloneVAppInVDCOptions() throws SecurityException, NoSuchMethodException, IOException {
      Method method = VCloudExpressAsyncClient.class.getMethod("cloneVAppInVDC", URI.class, URI.class, String.class,
            CloneVAppOptions[].class);
      HttpRequest request = processor.createRequest(method,
            URI.create("https://vcloud.safesecureweb.com/api/v0.8/vdc/1"),
            URI.create("https://vcloud.safesecureweb.com/api/v0.8/vapp/201"), "new-linux-server",
            new CloneVAppOptions().deploy().powerOn().withDescription("The description of the new vApp"));

      assertRequestLineEquals(request, "POST https://vcloud.safesecureweb.com/api/v0.8/vdc/1/action/cloneVApp HTTP/1.1");
      assertNonPayloadHeadersEqual(request, "Accept: application/vnd.vmware.vcloud.task+xml\n");
      assertPayloadEquals(request, Strings2.toStringAndClose(getClass().getResourceAsStream("/cloneVApp.xml")),
            "application/vnd.vmware.vcloud.cloneVAppParams+xml", false);

      assertResponseParserClassEquals(method, request, ParseSax.class);
      assertSaxResponseParserClassEquals(method, TaskHandler.class);
      assertExceptionParserClassEquals(method, null);

      checkFilters(request);
   }

   public void testOrg() throws SecurityException, NoSuchMethodException, IOException {
      Method method = VCloudExpressAsyncClient.class.getMethod("getOrg", URI.class);
      HttpRequest request = processor.createRequest(method,
            URI.create("https://vcloud.safesecureweb.com/api/v0.8/org/1"));

      assertRequestLineEquals(request, "GET https://vcloud.safesecureweb.com/api/v0.8/org/1 HTTP/1.1");
      assertNonPayloadHeadersEqual(request, "Accept: application/vnd.vmware.vcloud.org+xml\n");
      assertPayloadEquals(request, null, null, false);

      assertResponseParserClassEquals(method, request, ParseSax.class);
      assertSaxResponseParserClassEquals(method, OrgHandler.class);
      assertExceptionParserClassEquals(method, ReturnNullOnNotFoundOr404.class);

      checkFilters(request);
   }

   public void testFindOrgNamed() throws SecurityException, NoSuchMethodException, IOException {
      Method method = VCloudExpressAsyncClient.class.getMethod("findOrgNamed", String.class);
      HttpRequest request = processor.createRequest(method, "org");

      assertRequestLineEquals(request, "GET https://vcloud.safesecureweb.com/api/v0.8/org/1 HTTP/1.1");
      assertNonPayloadHeadersEqual(request, "Accept: application/vnd.vmware.vcloud.org+xml\n");
      assertPayloadEquals(request, null, null, false);

      assertResponseParserClassEquals(method, request, ParseSax.class);
      assertSaxResponseParserClassEquals(method, OrgHandler.class);
      assertExceptionParserClassEquals(method, ReturnNullOnNotFoundOr404.class);

      checkFilters(request);
   }

   public void testCatalog() throws SecurityException, NoSuchMethodException, IOException {
      Method method = VCloudExpressAsyncClient.class.getMethod("getCatalog", URI.class);
      HttpRequest request = processor.createRequest(method,
            URI.create("https://vcloud.safesecureweb.com/api/v0.8/catalog/1"));

      assertRequestLineEquals(request, "GET https://vcloud.safesecureweb.com/api/v0.8/catalog/1 HTTP/1.1");
      assertNonPayloadHeadersEqual(request, "Accept: application/vnd.vmware.vcloud.catalog+xml\n");
      assertPayloadEquals(request, null, null, false);

      assertResponseParserClassEquals(method, request, ParseSax.class);
      assertSaxResponseParserClassEquals(method, VCloudExpressCatalogHandler.class);
      assertExceptionParserClassEquals(method, ReturnNullOnNotFoundOr404.class);

      checkFilters(request);
   }

   public void testCatalogInOrg() throws SecurityException, NoSuchMethodException, IOException {
      Method method = VCloudExpressAsyncClient.class.getMethod("findCatalogInOrgNamed", String.class, String.class);
      HttpRequest request = processor.createRequest(method, "org", "catalog");

      assertRequestLineEquals(request, "GET https://vcloud.safesecureweb.com/api/v0.8/catalog/1 HTTP/1.1");
      assertNonPayloadHeadersEqual(request, "Accept: application/vnd.vmware.vcloud.catalog+xml\n");
      assertPayloadEquals(request, null, null, false);

      assertResponseParserClassEquals(method, request, ParseSax.class);
      assertSaxResponseParserClassEquals(method, VCloudExpressCatalogHandler.class);
      assertExceptionParserClassEquals(method, ReturnNullOnNotFoundOr404.class);

      checkFilters(request);
   }

   public void testNetwork() throws SecurityException, NoSuchMethodException, IOException {
      Method method = VCloudExpressAsyncClient.class.getMethod("getNetwork", URI.class);
      HttpRequest request = processor.createRequest(method,
            URI.create("https://vcloud.safesecureweb.com/api/v0.8/network/2"));

      assertRequestLineEquals(request, "GET https://vcloud.safesecureweb.com/api/v0.8/network/2 HTTP/1.1");
      assertNonPayloadHeadersEqual(request, "Accept: application/vnd.vmware.vcloud.network+xml\n");
      assertPayloadEquals(request, null, null, false);

      assertResponseParserClassEquals(method, request, ParseSax.class);
      assertSaxResponseParserClassEquals(method, OrgNetworkFromVCloudExpressNetworkHandler.class);
      assertExceptionParserClassEquals(method, ReturnNullOnNotFoundOr404.class);

      checkFilters(request);
   }

   public void testCatalogItemURI() throws SecurityException, NoSuchMethodException, IOException {
      Method method = VCloudExpressAsyncClient.class.getMethod("getCatalogItem", URI.class);
      HttpRequest request = processor.createRequest(method,
            URI.create("https://vcloud.safesecureweb.com/api/v0.8/catalogItem/2"));

      assertRequestLineEquals(request, "GET https://vcloud.safesecureweb.com/api/v0.8/catalogItem/2 HTTP/1.1");
      assertNonPayloadHeadersEqual(request, "Accept: application/vnd.vmware.vcloud.catalogItem+xml\n");
      assertPayloadEquals(request, null, null, false);

      assertResponseParserClassEquals(method, request, ParseSax.class);
      assertSaxResponseParserClassEquals(method, CatalogItemHandler.class);
      assertExceptionParserClassEquals(method, ReturnNullOnNotFoundOr404.class);

      checkFilters(request);
   }

   public void testFindCatalogItemInOrgCatalogNamed() throws SecurityException, NoSuchMethodException, IOException {
      Method method = VCloudExpressAsyncClient.class.getMethod("findCatalogItemInOrgCatalogNamed", String.class,
            String.class, String.class);
      HttpRequest request = processor.createRequest(method, "org", "catalog", "item");

      assertRequestLineEquals(request, "GET https://vcloud.safesecureweb.com/api/v0.8/catalogItem/1 HTTP/1.1");
      assertNonPayloadHeadersEqual(request, "Accept: application/vnd.vmware.vcloud.catalogItem+xml\n");
      assertPayloadEquals(request, null, null, false);

      assertResponseParserClassEquals(method, request, ParseSax.class);
      assertSaxResponseParserClassEquals(method, CatalogItemHandler.class);
      assertExceptionParserClassEquals(method, ReturnNullOnNotFoundOr404.class);

      checkFilters(request);
   }

   public void testFindVAppTemplate() throws SecurityException, NoSuchMethodException, IOException {
      Method method = VCloudExpressAsyncClient.class.getMethod("findVAppTemplateInOrgCatalogNamed", String.class,
            String.class, String.class);
      HttpRequest request = processor.createRequest(method, "org", "catalog", "template");

      assertRequestLineEquals(request, "GET https://vcloud.safesecureweb.com/api/v0.8/vAppTemplate/2 HTTP/1.1");
      assertNonPayloadHeadersEqual(request, "Accept: application/vnd.vmware.vcloud.vAppTemplate+xml\n");
      assertPayloadEquals(request, null, null, false);

      assertResponseParserClassEquals(method, request, ParseSax.class);
      assertSaxResponseParserClassEquals(method, VCloudExpressVAppTemplateHandler.class);
      assertExceptionParserClassEquals(method, ReturnNullOnNotFoundOr404.class);

      checkFilters(request);
   }
   
   public void testFindVAppTemplateNulls() throws SecurityException, NoSuchMethodException, IOException {
      Method method = VCloudExpressAsyncClient.class.getMethod("findVAppTemplateInOrgCatalogNamed", String.class,
            String.class, String.class);
      HttpRequest request = processor.createRequest(method, null, null, "template");

      assertRequestLineEquals(request, "GET https://vcloud.safesecureweb.com/api/v0.8/vAppTemplate/2 HTTP/1.1");
      assertNonPayloadHeadersEqual(request, "Accept: application/vnd.vmware.vcloud.vAppTemplate+xml\n");
      assertPayloadEquals(request, null, null, false);

      assertResponseParserClassEquals(method, request, ParseSax.class);
      assertSaxResponseParserClassEquals(method, VCloudExpressVAppTemplateHandler.class);
      assertExceptionParserClassEquals(method, ReturnNullOnNotFoundOr404.class);

      checkFilters(request);
   }
   public void testVAppTemplateURI() throws SecurityException, NoSuchMethodException, IOException {
      Method method = VCloudExpressAsyncClient.class.getMethod("getVAppTemplate", URI.class);
      HttpRequest request = processor.createRequest(method,
            URI.create("https://vcloud.safesecureweb.com/api/v0.8/vAppTemplate/2"));

      assertRequestLineEquals(request, "GET https://vcloud.safesecureweb.com/api/v0.8/vAppTemplate/2 HTTP/1.1");
      assertNonPayloadHeadersEqual(request, "Accept: application/vnd.vmware.vcloud.vAppTemplate+xml\n");
      assertPayloadEquals(request, null, null, false);

      assertResponseParserClassEquals(method, request, ParseSax.class);
      assertSaxResponseParserClassEquals(method, VCloudExpressVAppTemplateHandler.class);
      assertExceptionParserClassEquals(method, ReturnNullOnNotFoundOr404.class);

      checkFilters(request);
   }

   public void testFindVDCInOrgNamed() throws SecurityException, NoSuchMethodException, IOException {
      Method method = VCloudExpressAsyncClient.class.getMethod("findVDCInOrgNamed", String.class, String.class);
      HttpRequest request = processor.createRequest(method, "org", "vdc");

      assertRequestLineEquals(request, "GET https://vcloud.safesecureweb.com/api/v0.8/vdc/1 HTTP/1.1");
      assertNonPayloadHeadersEqual(request, "Accept: application/vnd.vmware.vcloud.vdc+xml\n");
      assertPayloadEquals(request, null, null, false);

      assertResponseParserClassEquals(method, request, ParseSax.class);
      assertSaxResponseParserClassEquals(method, VDCHandler.class);
      assertExceptionParserClassEquals(method, ReturnNullOnNotFoundOr404.class);

      checkFilters(request);
   }

   @Test(expectedExceptions = NoSuchElementException.class)
   public void testFindVDCInOrgNamedBadVDC() throws SecurityException, NoSuchMethodException, IOException {
      Method method = VCloudExpressAsyncClient.class.getMethod("findVDCInOrgNamed", String.class, String.class);
      processor.createRequest(method, "org", "vdc1");
   }

   @Test(expectedExceptions = NoSuchElementException.class)
   public void testFindVDCInOrgNamedBadOrg() throws SecurityException, NoSuchMethodException, IOException {
      Method method = VCloudExpressAsyncClient.class.getMethod("findVDCInOrgNamed", String.class, String.class);
      processor.createRequest(method, "org1", "vdc");
   }

   public void testFindVDCInOrgNamedNullOrg() throws SecurityException, NoSuchMethodException, IOException {
      Method method = VCloudExpressAsyncClient.class.getMethod("findVDCInOrgNamed", String.class, String.class);
      HttpRequest request = processor.createRequest(method, null, "vdc");

      assertRequestLineEquals(request, "GET https://vcloud.safesecureweb.com/api/v0.8/vdc/1 HTTP/1.1");
      assertNonPayloadHeadersEqual(request, "Accept: application/vnd.vmware.vcloud.vdc+xml\n");
      assertPayloadEquals(request, null, null, false);

      assertResponseParserClassEquals(method, request, ParseSax.class);
      assertSaxResponseParserClassEquals(method, VDCHandler.class);
      assertExceptionParserClassEquals(method, ReturnNullOnNotFoundOr404.class);

      checkFilters(request);
   }

   public void testFindVDCInOrgNamedNullOrgAndVDC() throws SecurityException, NoSuchMethodException, IOException {
      Method method = VCloudExpressAsyncClient.class.getMethod("findVDCInOrgNamed", String.class, String.class);
      HttpRequest request = processor.createRequest(method, null, null);

      assertRequestLineEquals(request, "GET https://vcloud.safesecureweb.com/api/v0.8/vdc/1 HTTP/1.1");
      assertNonPayloadHeadersEqual(request, "Accept: application/vnd.vmware.vcloud.vdc+xml\n");
      assertPayloadEquals(request, null, null, false);

      assertResponseParserClassEquals(method, request, ParseSax.class);
      assertSaxResponseParserClassEquals(method, VDCHandler.class);
      assertExceptionParserClassEquals(method, ReturnNullOnNotFoundOr404.class);

      checkFilters(request);
   }

   public void testGetVDC() throws SecurityException, NoSuchMethodException, IOException {
      Method method = VCloudExpressAsyncClient.class.getMethod("getVDC", URI.class);
      HttpRequest request = processor.createRequest(method,
            URI.create("https://vcloud.safesecureweb.com/api/v0.8/vdc/1"));

      assertRequestLineEquals(request, "GET https://vcloud.safesecureweb.com/api/v0.8/vdc/1 HTTP/1.1");
      assertNonPayloadHeadersEqual(request, "Accept: application/vnd.vmware.vcloud.vdc+xml\n");
      assertPayloadEquals(request, null, null, false);

      assertResponseParserClassEquals(method, request, ParseSax.class);
      assertSaxResponseParserClassEquals(method, VDCHandler.class);
      assertExceptionParserClassEquals(method, ReturnNullOnNotFoundOr404.class);

      checkFilters(request);
   }

   public void testGetTasksList() throws SecurityException, NoSuchMethodException, IOException {
      Method method = VCloudExpressAsyncClient.class.getMethod("getTasksList", URI.class);
      HttpRequest request = processor.createRequest(method,
            URI.create("https://vcloud.safesecureweb.com/api/v0.8/tasksList/1"));

      assertRequestLineEquals(request, "GET https://vcloud.safesecureweb.com/api/v0.8/tasksList/1 HTTP/1.1");
      assertNonPayloadHeadersEqual(request, "Accept: application/vnd.vmware.vcloud.tasksList+xml\n");
      assertPayloadEquals(request, null, null, false);

      assertResponseParserClassEquals(method, request, ParseSax.class);
      assertSaxResponseParserClassEquals(method, TasksListHandler.class);
      assertExceptionParserClassEquals(method, ReturnNullOnNotFoundOr404.class);

      checkFilters(request);
   }

   public void testFindTasksListInOrgNamed() throws SecurityException, NoSuchMethodException, IOException {
      Method method = VCloudExpressAsyncClient.class.getMethod("findTasksListInOrgNamed", String.class);
      HttpRequest request = processor.createRequest(method, "org");

      assertRequestLineEquals(request, "GET https://vcloud.safesecureweb.com/api/v0.8/tasksList/1 HTTP/1.1");
      assertNonPayloadHeadersEqual(request, "Accept: application/vnd.vmware.vcloud.tasksList+xml\n");
      assertPayloadEquals(request, null, null, false);

      assertResponseParserClassEquals(method, request, ParseSax.class);
      assertSaxResponseParserClassEquals(method, TasksListHandler.class);
      assertExceptionParserClassEquals(method, ReturnNullOnNotFoundOr404.class);

      checkFilters(request);
   }

   public void testDeployVApp() throws SecurityException, NoSuchMethodException, IOException {
      Method method = VCloudExpressAsyncClient.class.getMethod("deployVApp", URI.class);
      HttpRequest request = processor.createRequest(method,
            URI.create("https://vcloud.safesecureweb.com/api/v0.8/vApp/1"));

      assertRequestLineEquals(request, "POST https://vcloud.safesecureweb.com/api/v0.8/vApp/1/action/deploy HTTP/1.1");
      assertNonPayloadHeadersEqual(request, "Accept: application/vnd.vmware.vcloud.task+xml\n");
      assertPayloadEquals(request, null, null, false);

      assertResponseParserClassEquals(method, request, ParseSax.class);
      assertSaxResponseParserClassEquals(method, TaskHandler.class);
      assertExceptionParserClassEquals(method, null);

      checkFilters(request);
   }

   public void testGet() throws SecurityException, NoSuchMethodException, IOException {
      Method method = VCloudExpressAsyncClient.class.getMethod("getVApp", URI.class);
      HttpRequest request = processor.createRequest(method,
            URI.create("https://vcloud.safesecureweb.com/api/v0.8/vApp/1"));

      assertRequestLineEquals(request, "GET https://vcloud.safesecureweb.com/api/v0.8/vApp/1 HTTP/1.1");
      assertNonPayloadHeadersEqual(request, "Accept: application/vnd.vmware.vcloud.vApp+xml\n");
      assertPayloadEquals(request, null, null, false);

      assertResponseParserClassEquals(method, request, ParseSax.class);
      assertSaxResponseParserClassEquals(method, VCloudExpressVAppHandler.class);
      assertExceptionParserClassEquals(method, ReturnNullOnNotFoundOr404.class);

      checkFilters(request);
   }

   public void testUndeployVApp() throws SecurityException, NoSuchMethodException, IOException {
      Method method = VCloudExpressAsyncClient.class.getMethod("undeployVApp", URI.class);
      HttpRequest request = processor.createRequest(method,
            URI.create("https://vcloud.safesecureweb.com/api/v0.8/vApp/1"));

      assertRequestLineEquals(request, "POST https://vcloud.safesecureweb.com/api/v0.8/vApp/1/action/undeploy HTTP/1.1");
      assertNonPayloadHeadersEqual(request, "Accept: application/vnd.vmware.vcloud.task+xml\n");
      assertPayloadEquals(request, null, null, false);

      assertResponseParserClassEquals(method, request, ParseSax.class);
      assertSaxResponseParserClassEquals(method, TaskHandler.class);
      assertExceptionParserClassEquals(method, null);

      checkFilters(request);
   }

   public void testDelete() throws SecurityException, NoSuchMethodException, IOException {
      Method method = VCloudExpressAsyncClient.class.getMethod("deleteVApp", URI.class);
      HttpRequest request = processor.createRequest(method,
            URI.create("https://vcloud.safesecureweb.com/api/v0.8/vApp/1"));

      assertRequestLineEquals(request, "DELETE https://vcloud.safesecureweb.com/api/v0.8/vApp/1 HTTP/1.1");
      assertNonPayloadHeadersEqual(request, "");
      assertPayloadEquals(request, null, null, false);

      assertResponseParserClassEquals(method, request, ParseTaskFromLocationHeader.class);
      assertSaxResponseParserClassEquals(method, null);
      assertExceptionParserClassEquals(method, ReturnNullOnNotFoundOr404.class);

      checkFilters(request);
   }

   public void testPowerOnVApp() throws SecurityException, NoSuchMethodException, IOException {
      Method method = VCloudExpressAsyncClient.class.getMethod("powerOnVApp", URI.class);
      HttpRequest request = processor.createRequest(method,
            URI.create("https://vcloud.safesecureweb.com/api/v0.8/vApp/1"));

      assertRequestLineEquals(request,
            "POST https://vcloud.safesecureweb.com/api/v0.8/vApp/1/power/action/powerOn HTTP/1.1");
      assertNonPayloadHeadersEqual(request, "Accept: application/vnd.vmware.vcloud.task+xml\n");
      assertPayloadEquals(request, null, null, false);

      assertResponseParserClassEquals(method, request, ParseSax.class);
      assertSaxResponseParserClassEquals(method, TaskHandler.class);
      assertExceptionParserClassEquals(method, null);

      checkFilters(request);
   }

   public void testPowerOffVApp() throws SecurityException, NoSuchMethodException, IOException {
      Method method = VCloudExpressAsyncClient.class.getMethod("powerOffVApp", URI.class);
      HttpRequest request = processor.createRequest(method,
            URI.create("https://vcloud.safesecureweb.com/api/v0.8/vApp/1"));

      assertRequestLineEquals(request,
            "POST https://vcloud.safesecureweb.com/api/v0.8/vApp/1/power/action/powerOff HTTP/1.1");
      assertNonPayloadHeadersEqual(request, "Accept: application/vnd.vmware.vcloud.task+xml\n");
      assertPayloadEquals(request, null, null, false);

      assertResponseParserClassEquals(method, request, ParseSax.class);
      assertSaxResponseParserClassEquals(method, TaskHandler.class);
      assertExceptionParserClassEquals(method, null);

      checkFilters(request);
   }

   public void testResetVApp() throws SecurityException, NoSuchMethodException, IOException {
      Method method = VCloudExpressAsyncClient.class.getMethod("resetVApp", URI.class);
      HttpRequest request = processor.createRequest(method,
            URI.create("https://vcloud.safesecureweb.com/api/v0.8/vApp/1"));

      assertRequestLineEquals(request,
            "POST https://vcloud.safesecureweb.com/api/v0.8/vApp/1/power/action/reset HTTP/1.1");
      assertNonPayloadHeadersEqual(request, "Accept: application/vnd.vmware.vcloud.task+xml\n");
      assertPayloadEquals(request, null, null, false);

      assertResponseParserClassEquals(method, request, ParseSax.class);
      assertSaxResponseParserClassEquals(method, TaskHandler.class);
      assertExceptionParserClassEquals(method, null);

      checkFilters(request);
   }

   public void testSuspendVApp() throws SecurityException, NoSuchMethodException, IOException {
      Method method = VCloudExpressAsyncClient.class.getMethod("suspendVApp", URI.class);
      HttpRequest request = processor.createRequest(method,
            URI.create("https://vcloud.safesecureweb.com/api/v0.8/vApp/1"));

      assertRequestLineEquals(request,
            "POST https://vcloud.safesecureweb.com/api/v0.8/vApp/1/power/action/suspend HTTP/1.1");
      assertNonPayloadHeadersEqual(request, "Accept: application/vnd.vmware.vcloud.task+xml\n");
      assertPayloadEquals(request, null, null, false);

      assertResponseParserClassEquals(method, request, ParseSax.class);
      assertSaxResponseParserClassEquals(method, TaskHandler.class);
      assertExceptionParserClassEquals(method, null);

      checkFilters(request);
   }

   public void testShutdownVApp() throws SecurityException, NoSuchMethodException, IOException {
      Method method = VCloudExpressAsyncClient.class.getMethod("shutdownVApp", URI.class);
      HttpRequest request = processor.createRequest(method,
            URI.create("https://vcloud.safesecureweb.com/api/v0.8/vApp/1"));

      assertRequestLineEquals(request,
            "POST https://vcloud.safesecureweb.com/api/v0.8/vApp/1/power/action/shutdown HTTP/1.1");
      assertNonPayloadHeadersEqual(request, "");
      assertPayloadEquals(request, null, null, false);

      assertResponseParserClassEquals(method, request, ReleasePayloadAndReturn.class);
      assertSaxResponseParserClassEquals(method, null);
      assertExceptionParserClassEquals(method, null);

      checkFilters(request);
   }

   public void testGetTask() throws SecurityException, NoSuchMethodException, IOException {
      Method method = VCloudExpressAsyncClient.class.getMethod("getTask", URI.class);
      HttpRequest request = processor.createRequest(method,
            URI.create("https://vcloud.safesecureweb.com/api/v0.8/task/1"));

      assertRequestLineEquals(request, "GET https://vcloud.safesecureweb.com/api/v0.8/task/1 HTTP/1.1");
      assertNonPayloadHeadersEqual(request, "Accept: application/vnd.vmware.vcloud.task+xml\n");
      assertPayloadEquals(request, null, null, false);

      assertResponseParserClassEquals(method, request, ParseSax.class);
      assertSaxResponseParserClassEquals(method, TaskHandler.class);
      assertExceptionParserClassEquals(method, ReturnNullOnNotFoundOr404.class);

      checkFilters(request);
   }

   public void testCancelTask() throws SecurityException, NoSuchMethodException, IOException {
      Method method = VCloudExpressAsyncClient.class.getMethod("cancelTask", URI.class);
      HttpRequest request = processor.createRequest(method,
            URI.create("https://vcloud.safesecureweb.com/api/v0.8/task/1"));

      assertRequestLineEquals(request, "POST https://vcloud.safesecureweb.com/api/v0.8/task/1/action/cancel HTTP/1.1");
      assertNonPayloadHeadersEqual(request, "");
      assertPayloadEquals(request, null, null, false);

      assertResponseParserClassEquals(method, request, ReleasePayloadAndReturn.class);
      assertSaxResponseParserClassEquals(method, null);
      assertExceptionParserClassEquals(method, null);

      checkFilters(request);
   }

   @Override
   protected void checkFilters(HttpRequest request) {
      assertEquals(request.getFilters().size(), 1);
      assertEquals(request.getFilters().get(0).getClass(), SetVCloudTokenCookie.class);
   }

   @Override
   protected TypeLiteral<RestAnnotationProcessor<VCloudExpressAsyncClient>> createTypeLiteral() {
      return new TypeLiteral<RestAnnotationProcessor<VCloudExpressAsyncClient>>() {
      };
   }

   @Override
   protected Module createModule() {
      return new VCloudRestClientModuleExtension();
   }

   protected String provider = "vcloudexpress";

   @Override
   protected Properties getProperties() {
      Properties overrides = new Properties();
      overrides.setProperty(provider + ".endpoint", "https://vcloud.safesecureweb.com/api/v0.8");
      overrides.setProperty(provider + ".propertiesbuilder", VCloudExpressPropertiesBuilder.class.getName());
      overrides.setProperty(provider + ".contextbuilder", VCloudExpressContextBuilder.class.getName());
      return overrides;
   }

   @Override
   public RestContextSpec<?, ?> createContextSpec() {
      return new RestContextFactory(getProperties()).createContextSpec(provider, "identity", "credential",
            new Properties());
   }

   protected static final ReferenceTypeImpl ORG_REF = new ReferenceTypeImpl("org", VCloudMediaType.ORG_XML,
         URI.create("https://vcloud.safesecureweb.com/api/v0.8/org/1"));

   protected static final ReferenceTypeImpl CATALOG_REF = new ReferenceTypeImpl("catalog", VCloudMediaType.CATALOG_XML,
         URI.create("https://vcloud.safesecureweb.com/api/v0.8/catalog/1"));

   protected static final ReferenceTypeImpl TASKSLIST_REF = new ReferenceTypeImpl("tasksList",
         VCloudMediaType.TASKSLIST_XML, URI.create("https://vcloud.safesecureweb.com/api/v0.8/tasksList/1"));

   protected static final ReferenceTypeImpl VDC_REF = new ReferenceTypeImpl("vdc", VCloudMediaType.VDC_XML,
         URI.create("https://vcloud.safesecureweb.com/api/v0.8/vdc/1"));

   protected static final ReferenceTypeImpl NETWORK_REF = new ReferenceTypeImpl("network", VCloudMediaType.NETWORK_XML,
         URI.create("https://vcloud.safesecureweb.com/network/1990"));

   protected static final Org ORG = new OrgImpl(ORG_REF.getName(), ORG_REF.getType(), ORG_REF.getHref(), "org", null,
         ImmutableMap.<String, ReferenceType> of(CATALOG_REF.getName(), CATALOG_REF),
         ImmutableMap.<String, ReferenceType> of(VDC_REF.getName(), VDC_REF), ImmutableMap.<String, ReferenceType> of(
               NETWORK_REF.getName(), NETWORK_REF), TASKSLIST_REF, ImmutableList.<Task> of());

   protected static final VDC VDC = new VDCImpl(VDC_REF.getName(), VDC_REF.getType(), VDC_REF.getHref(),
         VDCStatus.READY, null, "description", ImmutableSet.<Task> of(), AllocationModel.ALLOCATION_POOL, null, null,
         null, ImmutableMap.<String, ReferenceType> of(
               "vapp",
               new ReferenceTypeImpl("vapp", "application/vnd.vmware.vcloud.vApp+xml", URI
                     .create("https://vcloud.safesecureweb.com/api/v0.8/vApp/188849-1")),
               "network",
               new ReferenceTypeImpl("network", "application/vnd.vmware.vcloud.vAppTemplate+xml", URI
                     .create("https://vcloud.safesecureweb.com/api/v0.8/vdcItem/2"))),
         ImmutableMap.<String, ReferenceType> of(NETWORK_REF.getName(), NETWORK_REF), 0, 0, 0, false);

   @RequiresHttp
   @ConfiguresRestClient
   public static class VCloudRestClientModuleExtension extends VCloudExpressRestClientModule {

      @Override
      protected URI provideAuthenticationURI(VCloudVersionsAsyncClient versionService, String version) {
         return URI.create("https://vcloud.safesecureweb.com/api/v0.8/login");
      }

      @Override
      protected Org provideOrg(Supplier<Map<String, ? extends Org>> orgSupplier,
            @org.jclouds.vcloud.endpoints.Org ReferenceType defaultOrg) {
         return ORG;
      }

      @Override
      protected void installDefaultVCloudEndpointsModule() {
         install(new AbstractModule() {

            @Override
            protected void configure() {
               bind(ReferenceType.class).annotatedWith(org.jclouds.vcloud.endpoints.Org.class).toInstance(ORG_REF);
               bind(ReferenceType.class).annotatedWith(org.jclouds.vcloud.endpoints.Catalog.class).toInstance(
                     CATALOG_REF);
               bind(ReferenceType.class).annotatedWith(org.jclouds.vcloud.endpoints.TasksList.class).toInstance(
                     TASKSLIST_REF);
               bind(ReferenceType.class).annotatedWith(org.jclouds.vcloud.endpoints.VDC.class).toInstance(VDC_REF);
               bind(ReferenceType.class).annotatedWith(org.jclouds.vcloud.endpoints.Network.class).toInstance(
                     NETWORK_REF);
            }

         });
      }

      @Override
      protected Supplier<VCloudSession> provideVCloudTokenCache(@Named(PROPERTY_SESSION_INTERVAL) long seconds,
            AtomicReference<AuthorizationException> authException, VCloudExpressLoginAsyncClient login) {
         return Suppliers.<VCloudSession> ofInstance(new VCloudSession() {

            @Override
            public Map<String, ReferenceType> getOrgs() {
               return ImmutableMap.<String, ReferenceType> of(ORG_REF.getName(), ORG_REF);
            }

            @Override
            public String getVCloudToken() {
               return "token";
            }

         });

      }

      @Override
      protected void configure() {
         super.configure();
         bind(OrgMapSupplier.class).to(TestOrgMapSupplier.class);
         bind(OrgCatalogSupplier.class).to(TestOrgCatalogSupplier.class);
         bind(OrgCatalogItemSupplier.class).to(TestOrgCatalogItemSupplier.class);
      }

      protected Supplier<Map<String, Map<String, ? extends org.jclouds.vcloud.domain.VDC>>> provideOrgVDCSupplierCache(
            @Named(PROPERTY_SESSION_INTERVAL) long seconds, final OrgVDCSupplier supplier) {
         return Suppliers.<Map<String, Map<String, ? extends org.jclouds.vcloud.domain.VDC>>> ofInstance(ImmutableMap
               .<String, Map<String, ? extends org.jclouds.vcloud.domain.VDC>> of(ORG_REF.getName(),
                     ImmutableMap.<String, org.jclouds.vcloud.domain.VDC> of(VDC.getName(), VDC)));
      }

      @Singleton
      public static class TestOrgMapSupplier extends OrgMapSupplier {

         @Inject
         protected TestOrgMapSupplier() {
            super(null, null);
         }

         @Override
         public Map<String, Org> get() {
            return ImmutableMap.<String, Org> of(ORG.getName(), ORG);
         }
      }

      @Singleton
      public static class TestOrgCatalogSupplier extends OrgCatalogSupplier {
         @Inject
         protected TestOrgCatalogSupplier() {
            super(null, null);
         }

         @Override
         public Map<String, Map<String, ? extends org.jclouds.vcloud.domain.Catalog>> get() {
            return ImmutableMap.<String, Map<String, ? extends org.jclouds.vcloud.domain.Catalog>> of(
                  ORG_REF.getName(), ImmutableMap.<String, org.jclouds.vcloud.domain.Catalog> of(
                        CATALOG_REF.getName(),
                        new CatalogImpl(CATALOG_REF.getName(), CATALOG_REF.getType(), CATALOG_REF.getHref(), null,
                              "description", ImmutableMap.<String, ReferenceType> of(
                                    "item",
                                    new ReferenceTypeImpl("item", "application/vnd.vmware.vcloud.catalogItem+xml", URI
                                          .create("https://vcloud.safesecureweb.com/api/v0.8/catalogItem/1")),
                                    "template",
                                    new ReferenceTypeImpl("template", "application/vnd.vmware.vcloud.vAppTemplate+xml",
                                          URI.create("https://vcloud.safesecureweb.com/api/v0.8/catalogItem/2"))),
                              ImmutableList.<Task> of(), true, false)));
         }
      }

      @Singleton
      public static class TestOrgCatalogItemSupplier extends OrgCatalogItemSupplier {
         protected TestOrgCatalogItemSupplier() {
            super(null, null);
         }

         @Override
         public Map<String, Map<String, Map<String, ? extends org.jclouds.vcloud.domain.CatalogItem>>> get() {
            return ImmutableMap.<String, Map<String, Map<String, ? extends org.jclouds.vcloud.domain.CatalogItem>>> of(
                  ORG_REF.getName(), ImmutableMap
                        .<String, Map<String, ? extends org.jclouds.vcloud.domain.CatalogItem>> of(CATALOG_REF
                              .getName(), ImmutableMap.<String, org.jclouds.vcloud.domain.CatalogItem> of(
                              "template",
                              new CatalogItemImpl("template", URI
                                    .create("https://vcloud.safesecureweb.com/api/v0.8/catalogItem/2"), "description",
                                    new ReferenceTypeImpl("template", "application/vnd.vmware.vcloud.vAppTemplate+xml",
                                          URI.create("https://vcloud.safesecureweb.com/api/v0.8/vAppTemplate/2")),
                                    ImmutableMap.<String, String> of()))));

         }
      }

   }

}