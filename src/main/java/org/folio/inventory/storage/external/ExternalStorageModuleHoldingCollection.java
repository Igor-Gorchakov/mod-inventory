package org.folio.inventory.storage.external;

import io.vertx.core.http.HttpClient;
import io.vertx.core.json.JsonObject;
import org.folio.inventory.domain.Holding;
import org.folio.inventory.domain.HoldingCollection;

import java.util.UUID;

class ExternalStorageModuleHoldingCollection
  extends ExternalStorageModuleCollection<Holding>
  implements HoldingCollection {

  ExternalStorageModuleHoldingCollection(
    HttpClient client,
    String baseAddress,
    String tenant,
    String token) {

    super(client, String.format("%s/%s", baseAddress, "holdings-storage/holdings"),
      tenant, token, "holdingsRecords");
  }

  @Override
  protected Holding mapFromJson(JsonObject holdingFromServer) {
    return new Holding(
      holdingFromServer.getString("id"),
      holdingFromServer.getString("instanceId"),
      holdingFromServer.getString("permanentLocationId"));
  }

  @Override
  protected String getId(Holding record) {
    return record.id;
  }

  @Override
  protected JsonObject mapToRequest(Holding holding) {
    JsonObject holdingToSend = new JsonObject();

    //TODO: Review if this shouldn't be defaulting here
    holdingToSend.put("id", holding.id != null
      ? holding.id
      : UUID.randomUUID().toString());

    includeIfPresent(holdingToSend, "instanceId", holding.instanceId);
    includeIfPresent(holdingToSend, "permanentLocationId", holding.permanentLocationId);

    return holdingToSend;
  }
}
