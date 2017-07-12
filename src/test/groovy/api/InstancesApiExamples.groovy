package api

import api.support.ApiRoot
import api.support.InstanceApiClient
import api.support.Preparation
import com.github.jsonldjava.core.DocumentLoader
import com.github.jsonldjava.core.JsonLdOptions
import com.github.jsonldjava.core.JsonLdProcessor
import io.vertx.core.json.JsonObject
import org.apache.http.impl.client.cache.CachingHttpClientBuilder
import org.apache.http.message.BasicHeader
import org.folio.inventory.support.JsonArrayHelper
import org.folio.inventory.support.http.client.OkapiHttpClient
import org.folio.inventory.support.http.client.Response
import org.folio.inventory.support.http.client.ResponseHandler
import spock.lang.Specification

import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

import static api.support.InstanceSamples.*
import static api.support.UrlUtility.toAbsolute
import static org.junit.Assert.assertThat
import static support.JsonObjectMatchers.hasSoleValidationError

class InstancesApiExamples extends Specification {
  private final OkapiHttpClient okapiClient = ApiTestSuite.createOkapiHttpClient()

  def setup() {
    new Preparation(okapiClient).deleteInstances()
  }

  void "Can create an instance"() {
    given:
      def newInstanceRequest = new JsonObject()
        .put("title", "Long Way to a Small Angry Planet")
        .put("identifiers", [[namespace: "isbn", value: "9781473619777"]])

    when:
      def postCompleted = new CompletableFuture<Response>()

      okapiClient.post(ApiRoot.instances(),
        newInstanceRequest, ResponseHandler.any(postCompleted))

      Response postResponse = postCompleted.get(5, TimeUnit.SECONDS);

    then:
      def location = postResponse.location

      assert postResponse.statusCode == 201
      assert location != null

      def getCompleted = new CompletableFuture<Response>()

      okapiClient.get(toAbsolute(location, ApiRoot.instances()),
        ResponseHandler.json(getCompleted))

      Response getResponse = getCompleted.get(5, TimeUnit.SECONDS);

      assert getResponse.statusCode == 200

      def createdInstance = getResponse.json

      assert createdInstance.containsKey("id")
      assert createdInstance.getString("title") == "Long Way to a Small Angry Planet"
      assert createdInstance.getJsonArray("identifiers").getJsonObject(0).getString("namespace") == "isbn"
      assert createdInstance.getJsonArray("identifiers").getJsonObject(0).getString("value") == "9781473619777"

//      expressesDublinCoreMetadata(createdInstance)
//      dublinCoreContextLinkRespectsWayResourceWasReached(createdInstance)
  }

  void "Can create an instance with an ID"() {
    given:
      def instanceId = UUID.randomUUID().toString()

      def newInstanceRequest = new JsonObject()
        .put("id", instanceId)
        .put("title", "Long Way to a Small Angry Planet")

    when:
      def postCompleted = new CompletableFuture<Response>()

      okapiClient.post(ApiRoot.instances(),
        newInstanceRequest, ResponseHandler.any(postCompleted))

      Response postResponse = postCompleted.get(5, TimeUnit.SECONDS);

    then:
      def location = postResponse.location

      assert postResponse.statusCode == 201
      assert location != null

      def getCompleted = new CompletableFuture<Response>()

      okapiClient.get(toAbsolute(location, ApiRoot.instances()),
        ResponseHandler.json(getCompleted))

      Response getResponse = getCompleted.get(5, TimeUnit.SECONDS)

      assert getResponse.statusCode == 200

      def createdInstance = getResponse.json

      assert createdInstance.getString("id") == instanceId
      assert createdInstance.getString("title") == "Long Way to a Small Angry Planet"

//      expressesDublinCoreMetadata(createdInstance)
  }

  void "Instance title is mandatory"() {
    given:
      def newInstanceRequest = new JsonObject()

    when:
      def postCompleted = new CompletableFuture<Response>()

    //Cannot use strict content-type due to RMB-34
      okapiClient.post(ApiRoot.instances(),
        newInstanceRequest, ResponseHandler.any(postCompleted))

      Response postResponse = postCompleted.get(5, TimeUnit.SECONDS);

    then:
      assert postResponse.statusCode == 422
      assert postResponse.location == null
      assertThat(JsonArrayHelper.toList(postResponse.json.getJsonArray("errors")),
        hasSoleValidationError("may not be null", "title"))
  }

  void "Can update an existing instance"() {

    given:
      def id = UUID.randomUUID()

      def newInstance = createInstance(smallAngryPlanet(id))

      def updateInstanceRequest = smallAngryPlanet(id)
        .put("title", "The Long Way to a Small, Angry Planet")

      def instanceLocation =
        new URL("${ApiRoot.instances()}/${newInstance.id}")

    when:
      def putCompleted = new CompletableFuture<Response>()

      okapiClient.put(instanceLocation,
        updateInstanceRequest, ResponseHandler.any(putCompleted))

      Response putResponse = putCompleted.get(5, TimeUnit.SECONDS);

    then:
      assert putResponse.statusCode == 204

      def getCompleted = new CompletableFuture<Response>()

      okapiClient.get(instanceLocation,
        ResponseHandler.json(getCompleted))

      Response getResponse = getCompleted.get(5, TimeUnit.SECONDS);

      assert getResponse.statusCode == 200

      def updatedInstance = getResponse.json

      assert updatedInstance.getString("id") == newInstance.id
      assert updatedInstance.getString("title") == "The Long Way to a Small, Angry Planet"
      assert updatedInstance.getJsonArray("identifiers").size() == 1
  }

  void "Can create an instance by putting to a new location"() {
    given:
      def updateInstanceRequest = smallAngryPlanet(UUID.randomUUID())

    when:
      def putCompleted = new CompletableFuture<Response>()

      okapiClient.put(new URL("${ApiRoot.instances()}/${updateInstanceRequest.getString("id")}"),
        updateInstanceRequest, ResponseHandler.any(putCompleted))

      Response putResponse = putCompleted.get(5, TimeUnit.SECONDS);

    then:
      assert putResponse.statusCode == 204
  }

  void "Can delete all instances"() {
    given:
      createInstance(smallAngryPlanet(UUID.randomUUID()))
      createInstance(nod(UUID.randomUUID()))
      createInstance(leviathanWakes(UUID.randomUUID()))

    when:
      def deleteCompleted = new CompletableFuture<Response>()

      okapiClient.delete(ApiRoot.instances(),
        ResponseHandler.any(deleteCompleted))

      Response deleteResponse = deleteCompleted.get(5, TimeUnit.SECONDS);
    then:
      assert deleteResponse.statusCode == 204
      assert deleteResponse.hasBody() == false

      def getAllCompleted = new CompletableFuture<Response>()

      okapiClient.get(ApiRoot.instances(),
        ResponseHandler.json(getAllCompleted))

      Response getAllResponse = getAllCompleted.get(5, TimeUnit.SECONDS);

      assert getAllResponse.json.getJsonArray("instances").size() == 0
      assert getAllResponse.json.getInteger("totalRecords") == 0
  }

  void "Can delete a single instance"() {
    given:
      createInstance(smallAngryPlanet(UUID.randomUUID()))
      createInstance(nod(UUID.randomUUID()))
      def instanceToDelete = createInstance(leviathanWakes(UUID.randomUUID()))

      def instanceToDeleteLocation =
        new URL("${ApiRoot.instances()}/${instanceToDelete.id}")

    when:
      def deleteCompleted = new CompletableFuture<Response>()

      okapiClient.delete(instanceToDeleteLocation,
        ResponseHandler.any(deleteCompleted))

      Response deleteResponse = deleteCompleted.get(5, TimeUnit.SECONDS);

    then:
      assert deleteResponse.statusCode == 204
      assert deleteResponse.hasBody() == false

      def getCompleted = new CompletableFuture<Response>()

      okapiClient.get(instanceToDeleteLocation,
        ResponseHandler.any(getCompleted))

      Response getResponse = getCompleted.get(5, TimeUnit.SECONDS)

      assert getResponse.statusCode == 404

      def getAllCompleted = new CompletableFuture<Response>()

      okapiClient.get(ApiRoot.instances(),
        ResponseHandler.json(getAllCompleted))

      Response getAllResponse = getAllCompleted.get(5, TimeUnit.SECONDS);

      assert getAllResponse.json.getJsonArray("instances").size() == 2
      assert getAllResponse.json.getInteger("totalRecords") == 2
  }

  void "Can get all instances"() {
    given:
      createInstance(smallAngryPlanet(UUID.randomUUID()))
      createInstance(nod(UUID.randomUUID()))
      createInstance(temeraire(UUID.randomUUID()))

    when:
      def getAllCompleted = new CompletableFuture<Response>()

      okapiClient.get(ApiRoot.instances(),
        ResponseHandler.json(getAllCompleted))

      Response getAllResponse = getAllCompleted.get(5, TimeUnit.SECONDS)

    then:
      assert getAllResponse.statusCode == 200

      def instances = JsonArrayHelper.toList(getAllResponse.json.getJsonArray("instances"))

      assert instances.size() == 3
      assert getAllResponse.json.getInteger("totalRecords") == 3

      hasCollectionProperties(instances)
  }

  void "Can page all instances"() {
    given:
      createInstance(smallAngryPlanet(UUID.randomUUID()))
      createInstance(nod(UUID.randomUUID()))
      createInstance(temeraire(UUID.randomUUID()))
      createInstance(leviathanWakes(UUID.randomUUID()))
      createInstance(taoOfPooh(UUID.randomUUID()))

    when:
      def firstPageGetCompleted = new CompletableFuture<Response>()
      def secondPageGetCompleted = new CompletableFuture<Response>()

      okapiClient.get(ApiRoot.instances("limit=3"),
        ResponseHandler.json(firstPageGetCompleted))

      okapiClient.get(ApiRoot.instances("limit=3&offset=3"),
        ResponseHandler.json(secondPageGetCompleted))

      Response firstPageResponse = firstPageGetCompleted.get(5, TimeUnit.SECONDS)
      Response secondPageResponse = secondPageGetCompleted.get(5, TimeUnit.SECONDS)

    then:
      assert firstPageResponse.statusCode == 200
      assert secondPageResponse.statusCode == 200

      def firstPageInstances = JsonArrayHelper.toList(firstPageResponse.json.getJsonArray("instances"))

      assert firstPageInstances.size() == 3
      assert firstPageResponse.json.getInteger("totalRecords") == 5
      hasCollectionProperties(firstPageInstances)

      def secondPageInstances = JsonArrayHelper.toList(secondPageResponse.json.getJsonArray("instances"))

      assert secondPageInstances.size() == 2
      assert secondPageResponse.json.getInteger("totalRecords") == 5
      hasCollectionProperties(secondPageInstances)
  }

  void "Page parameters must be numeric"() {
    when:
      def getPagedCompleted = new CompletableFuture<Response>()

    //Cannot use strict content-type due to RMB-34
      okapiClient.get(ApiRoot.instances("limit=&offset="),
        ResponseHandler.any(getPagedCompleted))

      Response getPagedResponse = getPagedCompleted.get(5, TimeUnit.SECONDS)

    then:
      assert getPagedResponse.statusCode == 400
    //Error messages are different
//      assert getPagedResponse.body == "limit and offset must be numeric when supplied"
  }

  void "Can search for instances by title"() {
    given:
      createInstance(smallAngryPlanet(UUID.randomUUID()))
      createInstance(nod(UUID.randomUUID()))
      createInstance(uprooted(UUID.randomUUID()))

    when:
      def searchGetCompleted = new CompletableFuture<Response>()

      okapiClient.get(ApiRoot.instances("query=title=*Small%20Angry*"),
        ResponseHandler.json(searchGetCompleted))

      Response searchGetResponse = searchGetCompleted.get(5, TimeUnit.SECONDS)
    then:
      assert searchGetResponse.statusCode == 200

      def instances = JsonArrayHelper.toList(searchGetResponse.json.getJsonArray("instances"))

      assert instances.size() == 1
      assert searchGetResponse.json.getInteger("totalRecords") == 1
      assert instances[0].getString("title") == "Long Way to a Small Angry Planet"

      hasCollectionProperties(instances)
  }

  void "Cannot find an unknown resource"() {
    when:
      def getCompleted = new CompletableFuture<Response>()

      okapiClient.get("${ApiRoot.instances()}/${UUID.randomUUID()}",
        ResponseHandler.any(getCompleted))

      Response getResponse = getCompleted.get(5, TimeUnit.SECONDS);

    then:
      assert getResponse.statusCode == 404
  }

  private void hasCollectionProperties(instances) {
//    instances.each {
//      expressesDublinCoreMetadata(it)
//    }
  }

  private void expressesDublinCoreMetadata(JsonObject instance) {
    def options = new JsonLdOptions()
    def documentLoader = new DocumentLoader()
    def httpClient = CachingHttpClientBuilder
      .create()
      .setDefaultHeaders([new BasicHeader('X-Okapi-Tenant', ApiTestSuite.TENANT_ID)])
      .build()

    documentLoader.setHttpClient(httpClient)

    options.setDocumentLoader(documentLoader)

    def expandedLinkedData = JsonLdProcessor.expand(instance.getMap(), options)

    assert expandedLinkedData.empty == false: "No Linked Data present"
    assert LinkedDataValue(expandedLinkedData,
      "http://purl.org/dc/terms/title") == instance.getString("title")
  }

  private static String LinkedDataValue(List<Object> expanded, String field) {
    expanded[0][field][0]?."@value"
  }

  private def createInstance(JsonObject newInstanceRequest) {
    InstanceApiClient.createInstance(okapiClient, newInstanceRequest)
  }
}
