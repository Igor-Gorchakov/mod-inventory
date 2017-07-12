package org.folio.inventory.resources

import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import io.vertx.ext.web.handler.BodyHandler
import org.codehaus.plexus.util.StringUtils
import org.folio.inventory.common.WebRoutingContext
import org.folio.inventory.common.api.request.PagingParameters
import org.folio.inventory.common.api.request.VertxBodyParser
import org.folio.inventory.common.api.response.*
import org.folio.inventory.common.domain.Failure
import org.folio.inventory.common.domain.Success
import org.folio.inventory.domain.Item
import org.folio.inventory.storage.Storage
import org.folio.inventory.support.CollectionResourceClient
import org.folio.inventory.support.http.client.OkapiHttpClient
import org.folio.inventory.support.http.client.Response

import java.util.concurrent.CompletableFuture
import java.util.stream.Collectors
import java.util.stream.Stream

class Items {

  private final Storage storage

  Items(final Storage storage) {
    this.storage = storage
  }

  void register(Router router) {
    router.post(relativeItemsPath() + "*").handler(BodyHandler.create())
    router.put(relativeItemsPath() + "*").handler(BodyHandler.create())

    router.get(relativeItemsPath()).handler(this.&getAll)
    router.post(relativeItemsPath()).handler(this.&create)
    router.delete(relativeItemsPath()).handler(this.&deleteAll)

    router.get(relativeItemsPath() + "/:id").handler(this.&getById)
    router.put(relativeItemsPath() + "/:id").handler(this.&update)
    router.delete(relativeItemsPath() + "/:id").handler(this.&deleteById)
  }

  void getAll(RoutingContext routingContext) {
    def context = new WebRoutingContext(routingContext)

    def search = context.getStringParameter("query", null)

    if(routingContext.request().params().contains("offset") && StringUtils.isEmpty(routingContext.request().getParam("offset"))) {
      ClientErrorResponse.badRequest(routingContext.response(),
        "offset does not have a default value in the RAML and has been passed empty")
      return
    }

    if(routingContext.request().params().contains("limit") && StringUtils.isEmpty(routingContext.request().getParam("limit"))) {
      ClientErrorResponse.badRequest(routingContext.response(),
        "limit does not have a default value in the RAML and has been passed empty")
      return
    }

    def pagingParameters = PagingParameters.from(context)

    if(search == null) {
      storage.getItemCollection(context).findAll(
        pagingParameters,
        { Success success ->
          respondWithManyItems(routingContext, context, success.result)
        }, FailureResponseConsumer.serverError(routingContext.response()))
    }
    else {
      storage.getItemCollection(context).findByCql(search,
        pagingParameters, { Success success ->
        respondWithManyItems(routingContext, context, success.result)
      }, FailureResponseConsumer.serverError(routingContext.response()))
    }
  }

  void deleteAll(RoutingContext routingContext) {
    def context = new WebRoutingContext(routingContext)

    storage.getItemCollection(context).empty(
      { SuccessResponse.noContent(routingContext.response()) },
      FailureResponseConsumer.serverError(routingContext.response()))
  }

  void create(RoutingContext routingContext) {
    def context = new WebRoutingContext(routingContext)

    Map itemRequest = new VertxBodyParser().toMap(routingContext)

    def newItem = requestToItem(itemRequest.item)

    def itemCollection = storage.getItemCollection(context)

    if(newItem.barcode != null) {
      itemCollection.findByCql("barcode=${newItem.barcode}",
        PagingParameters.defaults(), {

        if(it.result.items.size() == 0) {
          itemCollection.add(newItem, { Success success ->
            RedirectResponse.created(routingContext.response(),
              context.absoluteUrl(
                "${relativeItemsPath()}/${success.result.id}").toString())
          }, FailureResponseConsumer.serverError(routingContext.response()))
        }
        else {
          ClientErrorResponse.badRequest(routingContext.response(),
            "Barcodes must be unique, ${newItem.barcode} is already assigned to another item")
        }
      }, FailureResponseConsumer.serverError(routingContext.response()))
    }
    else {
      itemCollection.add(newItem, { Success success ->
        RedirectResponse.created(routingContext.response(),
          context.absoluteUrl(
            "${relativeItemsPath()}/${success.result.id}").toString())
      }, FailureResponseConsumer.serverError(routingContext.response()))
    }
  }

  void update(RoutingContext routingContext) {
    def context = new WebRoutingContext(routingContext)

    Map itemRequest = new VertxBodyParser().toMap(routingContext)

    def updatedItem = requestToItem(itemRequest.item)

    def itemCollection = storage.getItemCollection(context)

    itemCollection.findById(routingContext.request().getParam("id"), {
      Success it ->
      if(it.result != null) {
        if(updatedItem.barcode == null || it.result.barcode == updatedItem.barcode) {
          itemCollection.update(updatedItem,
            { SuccessResponse.noContent(routingContext.response()) },
            { Failure failure -> ServerErrorResponse.internalError(
              routingContext.response(), failure.reason) })
        } else {
          itemCollection.findByCql("barcode=${updatedItem.barcode}",
            PagingParameters.defaults(), {

            if(it.result.items.size() == 1 && it.result.items.first().id == updatedItem.id) {
              itemCollection.update(updatedItem, {
                SuccessResponse.noContent(routingContext.response()) },
                { Failure failure -> ServerErrorResponse.internalError(
                  routingContext.response(), failure.reason) })
            }
            else {
              ClientErrorResponse.badRequest(routingContext.response(),
                "Barcodes must be unique, ${updatedItem.barcode} is already assigned to another item")
            }
          }, FailureResponseConsumer.serverError(routingContext.response()))
        }
      }
      else {
        ClientErrorResponse.notFound(routingContext.response())
      }
    }, FailureResponseConsumer.serverError(routingContext.response()))
  }

  void deleteById(RoutingContext routingContext) {
    def context = new WebRoutingContext(routingContext)

    storage.getItemCollection(context).delete(
      routingContext.request().getParam("id"),
      { SuccessResponse.noContent(routingContext.response()) },
      FailureResponseConsumer.serverError(routingContext.response()))
  }

  void getById(RoutingContext routingContext) {
    def context = new WebRoutingContext(routingContext)

    def materialTypesClient = createMaterialTypesClient(routingContext, context)
    def loanTypesClient = createLoanTypesClient(routingContext, context)

    storage.getItemCollection(context).findById(
      routingContext.request().getParam("id"),
      { Success itemResponse ->
        def item = itemResponse.result

        if(item != null) {
          def materialTypeFuture = new CompletableFuture<Response>()
          def permanentLoanTypeFuture = new CompletableFuture<Response>()
          def temporaryLoanTypeFuture = new CompletableFuture<Response>()
          def allFutures = new ArrayList<CompletableFuture<Response>>()

          if(item?.materialTypeId != null) {
            allFutures.add(materialTypeFuture)

            materialTypesClient.get(item?.materialTypeId,
              { response -> materialTypeFuture.complete(response) })
          }

          if(item?.permanentLoanTypeId != null) {
            allFutures.add(permanentLoanTypeFuture)

            loanTypesClient.get(item?.permanentLoanTypeId,
              { response -> permanentLoanTypeFuture.complete(response) })
          }

          if(item?.temporaryLoanTypeId != null) {
            allFutures.add(temporaryLoanTypeFuture)

            loanTypesClient.get(item?.temporaryLoanTypeId,
              { response -> temporaryLoanTypeFuture.complete(response) })
          }

          CompletableFuture<Void> allDoneFuture = CompletableFuture.allOf(*allFutures)

          allDoneFuture.thenAccept({ v ->

            def foundMaterialType = item?.materialTypeId != null &&
              materialTypeFuture.join().statusCode == 200 ?
              materialTypeFuture.join().json : null

            def foundPermanentLoanType = item?.permanentLoanTypeId != null &&
              permanentLoanTypeFuture.join().statusCode == 200 ?
              permanentLoanTypeFuture.join().json : null

            def foundTemporaryLoanType = item?.temporaryLoanTypeId != null &&
              temporaryLoanTypeFuture.join().statusCode == 200 ?
              temporaryLoanTypeFuture.join().json : null

            JsonResponse.success(routingContext.response(),
              new ItemRepresentation().toJson(item,
                foundMaterialType, foundPermanentLoanType,
                foundTemporaryLoanType))
          })
        }
        else {
          ClientErrorResponse.notFound(routingContext.response())
        }
      }, FailureResponseConsumer.serverError(routingContext.response()))
  }

  private CollectionResourceClient createMaterialTypesClient(
    RoutingContext routingContext,
    WebRoutingContext context) {

    def client = new OkapiHttpClient(routingContext.vertx().createHttpClient(),
      new URL(context.okapiLocation), context.tenantId,
      context.token,
      {
        ServerErrorResponse.internalError(routingContext.response(),
          "Failed to retrieve material types: ${it}")
      })

    new CollectionResourceClient(client,
      new URL(context.okapiLocation + "/material-types"))
  }

  private CollectionResourceClient createLoanTypesClient(
    RoutingContext routingContext,
    WebRoutingContext context) {

    def client = new OkapiHttpClient(routingContext.vertx().createHttpClient(),
      new URL(context.okapiLocation), context.tenantId,
      context.token,
      {
        ServerErrorResponse.internalError(routingContext.response(),
          "Failed to retrieve loan types: ${it}")
      })

    new CollectionResourceClient(client,
      new URL(context.okapiLocation + "/loan-types"))
  }


  private static String relativeItemsPath() {
    "/inventory/items"
  }

  private Item requestToItem(Map<String, Object> itemRequest) {
    new Item(itemRequest.id, itemRequest.title,
      itemRequest.barcode, itemRequest.instanceId, itemRequest?.status?.name,
      itemRequest?.materialTypeId, itemRequest?.location?.name,
      itemRequest?.permanentLoanTypeId, itemRequest?.temporaryLoanTypeId)
  }

  private respondWithManyItems(
    RoutingContext routingContext,
    WebRoutingContext context,
    Map wrappedItems) {

    def materialTypesClient = createMaterialTypesClient(routingContext, context)
    def loanTypesClient = createLoanTypesClient(routingContext, context)

    def allMaterialTypeFutures = new ArrayList<CompletableFuture<Response>>()
    def allLoanTypeFutures = new ArrayList<CompletableFuture<Response>>()
    def allFutures = new ArrayList<CompletableFuture<Response>>()

    def materialTypeIds = wrappedItems.items.stream()
      .map({ it?.materialTypeId })
      .filter({ it != null })
      .distinct()
      .collect(Collectors.toList())

    materialTypeIds.each { id ->
      def newFuture = new CompletableFuture<>()

      allFutures.add(newFuture)
      allMaterialTypeFutures.add(newFuture)

      materialTypesClient.get(id, { response -> newFuture.complete(response) })
    }

    def permanentLoanTypeIds = wrappedItems.items.stream()
      .map({ it?.permanentLoanTypeId })
      .filter({ it != null })

    def temporaryLoanTypeIds = wrappedItems.items.stream()
      .map({ it?.temporaryLoanTypeId })
      .filter({ it != null })

    Stream.concat(permanentLoanTypeIds, temporaryLoanTypeIds)
      .distinct()
      .each { id ->
      def newFuture = new CompletableFuture<>()

      allFutures.add(newFuture)
      allLoanTypeFutures.add(newFuture)

      loanTypesClient.get(id, { response -> newFuture.complete(response) })
    }

    CompletableFuture<Void> allDoneFuture = CompletableFuture.allOf(*allFutures)

    allDoneFuture.thenAccept({ v ->
      def materialTypeResponses = allMaterialTypeFutures.stream()
        .map({ future -> future.join() })
        .collect(Collectors.toList())

      def loanTypeResponses = allLoanTypeFutures.stream()
        .map({ future -> future.join() })
        .collect(Collectors.toList())

      def foundMaterialTypes = materialTypeResponses.stream()
        .filter({ it.getStatusCode() == 200 })
        .map({ it.getJson() })
        .collect(Collectors.toMap({ it.getString("id") }, { it }))

      def foundLoanTypes = loanTypeResponses.stream()
        .filter({ it.getStatusCode() == 200 })
        .map({ it.getJson() })
        .collect(Collectors.toMap({ it.getString("id") }, { it }))

      JsonResponse.success(routingContext.response(),
        new ItemRepresentation()
          .toJson(wrappedItems, foundMaterialTypes, foundLoanTypes))
    })
  }
}
