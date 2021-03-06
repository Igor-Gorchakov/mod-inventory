package api.support.builders;

import api.ApiTestSuite;
import io.vertx.core.json.JsonObject;

import java.util.UUID;

public class HoldingRequestBuilder implements Builder {

  private final UUID instanceId;
  private final UUID permanentLocationId;
  private final UUID temporaryLocationId;
  private final String callNumber;

  public HoldingRequestBuilder() {
    this(
      null,
      UUID.fromString(ApiTestSuite.getThirdFloorLocation()),
      UUID.fromString(ApiTestSuite.getReadingRoomLocation()),
      null);
  }

  private HoldingRequestBuilder(
    UUID instanceId,
    UUID permanentLocationId,
    UUID temporaryLocationId,
    String callNumber) {

    this.instanceId = instanceId;
    this.permanentLocationId = permanentLocationId;
    this.temporaryLocationId = temporaryLocationId;
    this.callNumber = callNumber;
  }

  @Override
  public JsonObject create() {
    JsonObject holding = new JsonObject();

    holding.put("instanceId", instanceId.toString())
           .put("permanentLocationId", permanentLocationId.toString());

    if (temporaryLocationId != null) {
      holding.put("temporaryLocationId", temporaryLocationId.toString());
    }

    holding.put("callNumber", callNumber);

    return holding;
  }

  private HoldingRequestBuilder withPermanentLocation(UUID permanentLocationId) {
    return new HoldingRequestBuilder(
      this.instanceId,
      permanentLocationId,
      this.temporaryLocationId,
      callNumber);
  }

  private HoldingRequestBuilder withTemporaryLocation(UUID temporaryLocationId) {
    return new HoldingRequestBuilder(
      this.instanceId,
      this.permanentLocationId,
      temporaryLocationId,
      this.callNumber);
  }

  public HoldingRequestBuilder permanentlyInMainLibrary() {
    return withPermanentLocation(UUID.fromString(ApiTestSuite.getMainLibraryLocation()));
  }

  public HoldingRequestBuilder temporarilyInMezzanine() {
    return withTemporaryLocation(UUID.fromString(ApiTestSuite.getMezzanineDisplayCaseLocation()));
  }

  public HoldingRequestBuilder withNoTemporaryLocation() {
    return withTemporaryLocation(null);
  }

  public HoldingRequestBuilder forInstance(UUID instanceId) {
    return new HoldingRequestBuilder(
      instanceId,
      this.permanentLocationId,
      this.temporaryLocationId,
      this.callNumber);
  }

  public HoldingRequestBuilder withCallNumber(String callNumber) {
    return new HoldingRequestBuilder(
      this.instanceId,
      this.permanentLocationId,
      this.temporaryLocationId,
      callNumber);
  }
}
