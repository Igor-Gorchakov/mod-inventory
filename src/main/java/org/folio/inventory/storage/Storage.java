package org.folio.inventory.storage;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import org.folio.inventory.common.Context;
import org.folio.inventory.domain.CollectionProvider;
import org.folio.inventory.domain.HoldingCollection;
import org.folio.inventory.domain.InstanceCollection;
import org.folio.inventory.domain.ItemCollection;
import org.folio.inventory.domain.ingest.IngestJobCollection;
import org.folio.inventory.storage.external.ExternalStorageCollections;

import java.util.function.Function;

public class Storage {
  private final Function<Context, CollectionProvider> providerFactory;

  private Storage(final Function<Context, CollectionProvider> providerFactory) {
    this.providerFactory = providerFactory;
  }

  public static Storage basedUpon(Vertx vertx, JsonObject config) {
    String storageType = config.getString("storage.type", "okapi");

    switch(storageType) {
      case "external":
        String location = config.getString("storage.location", null);

        if(location == null) {
          throw new IllegalArgumentException(
            "For external storage, location must be provided.");
        }

        return new Storage(context -> new ExternalStorageCollections(vertx, location));

      case "okapi":
        return new Storage(context ->
          new ExternalStorageCollections(vertx, context.getOkapiLocation()));

      default:
        throw new IllegalArgumentException("Storage type must be one of [external, okapi]");
    }
  }

  public ItemCollection getItemCollection(Context context) {
    return providerFactory.apply(context).getItemCollection(
      context.getTenantId(), context.getToken());
  }

  public InstanceCollection getInstanceCollection(Context context) {
    return providerFactory.apply(context).getInstanceCollection(
      context.getTenantId(), context.getToken());
  }

  public IngestJobCollection getIngestJobCollection(Context context) {
    return providerFactory.apply(context).getIngestJobCollection(
      context.getTenantId(), context.getToken());
  }

  public HoldingCollection getHoldingCollection(Context context) {
    return providerFactory.apply(context).getHoldingCollection(
      context.getTenantId(), context.getToken());
  }
}
