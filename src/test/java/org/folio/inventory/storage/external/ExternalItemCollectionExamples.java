package org.folio.inventory.storage.external;

import org.apache.commons.lang3.StringUtils;
import org.folio.inventory.common.WaitForAllFutures;
import org.folio.inventory.common.api.request.PagingParameters;
import org.folio.inventory.common.domain.MultipleRecords;
import org.folio.inventory.domain.Item;
import org.folio.inventory.domain.ItemCollection;
import org.junit.Before;
import org.junit.Test;

import java.io.UnsupportedEncodingException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import static org.folio.inventory.common.FutureAssistance.*;
import static org.folio.inventory.storage.external.ExternalStorageSuite.getStorageAddress;
import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.assertThat;

public class ExternalItemCollectionExamples {

  private final String bookMaterialTypeId = UUID.randomUUID().toString();
  private final String canCirculateLoanTypeId = UUID.randomUUID().toString();

  private final String mainLibraryLocationId = UUID.randomUUID().toString();
  private final String annexLibraryLocationId = UUID.randomUUID().toString();

  private static final String tenantId = "test_tenant";
  private static final String tenantToken = "eyJhbGciOiJIUzUxMiJ9.eyJzdWIiOiJhZG1pbiIsInRlbmFudCI6ImRlbW9fdGVuYW50In0.29VPjLI6fLJzxQW0UhQ0jsvAn8xHz501zyXAxRflXfJ9wuDzT8TDf-V75PjzD7fe2kHjSV2dzRXbstt3BTtXIQ";

  private final ItemCollection collection =
    ExternalStorageSuite.useVertx(
      it -> new ExternalStorageModuleItemCollection(it, getStorageAddress(),
        tenantId, tenantToken));

  private final Item smallAngryPlanet = smallAngryPlanet();
  private final Item nod = nod();
  private final Item uprooted = uprooted();
  private final Item temeraire = temeraire();
  private final Item interestingTimes = interestingTimes();

  @Before
  public void before()
    throws InterruptedException, ExecutionException, TimeoutException {

    CompletableFuture<Void> emptied = new CompletableFuture<>();

    collection.empty(succeed(emptied), fail(emptied));

    waitForCompletion(emptied);
  }

  @Test
  public void canBeEmptied()
    throws InterruptedException, ExecutionException, TimeoutException {

    addSomeExamples(collection);

    CompletableFuture<Void> emptied = new CompletableFuture<>();

    collection.empty(succeed(emptied), fail(emptied));

    waitForCompletion(emptied);

    CompletableFuture<MultipleRecords<Item>> findFuture = new CompletableFuture<>();

    collection.findAll(PagingParameters.defaults(),
      succeed(findFuture), fail(findFuture));

    MultipleRecords<Item> wrappedItems = getOnCompletion(findFuture);

    List<Item> allItems = wrappedItems.records;

    assertThat(allItems.size(), is(0));
    assertThat(wrappedItems.totalRecords, is(0));
  }

  @Test
  public void anItemCanBeAdded()
    throws InterruptedException, ExecutionException, TimeoutException {

    addSomeExamples(collection);

    CompletableFuture<MultipleRecords<Item>> findFuture = new CompletableFuture<>();

    collection.findAll(PagingParameters.defaults(), succeed(findFuture),
      fail(findFuture));

    MultipleRecords<Item> wrappedItems = getOnCompletion(findFuture);

    List<Item> allItems = wrappedItems.records;

    assertThat(allItems.size(), is(3));
    assertThat(wrappedItems.totalRecords, is(3));

    Item smallAngry = getItem(allItems, "Long Way to a Small Angry Planet");

    assertThat(smallAngry, notNullValue());
    assertThat(smallAngry.barcode, is("036000291452"));
    assertThat(smallAngry.status, is("Available"));
    assertThat(smallAngry.instanceId, is(notNullValue()));
    assertThat(smallAngry.materialTypeId, is(bookMaterialTypeId));
    assertThat(smallAngry.permanentLoanTypeId, is(canCirculateLoanTypeId));
    assertThat(smallAngry.permanentLocationId, is(mainLibraryLocationId));
    assertThat(smallAngry.temporaryLocationId, is(annexLibraryLocationId));

    Item nod = getItem(allItems, "Nod");

    assertThat(nod, notNullValue());
    assertThat(nod.barcode, is("565578437802"));
    assertThat(nod.status, is("Available"));
    assertThat(nod.instanceId, is(notNullValue()));
    assertThat(nod.materialTypeId, is(bookMaterialTypeId));
    assertThat(nod.permanentLoanTypeId, is(canCirculateLoanTypeId));
    assertThat(nod.permanentLocationId, is(mainLibraryLocationId));
    assertThat(nod.temporaryLocationId, is(annexLibraryLocationId));

    Item uprooted = getItem(allItems, "Uprooted");

    assertThat(uprooted, notNullValue());
    assertThat(uprooted.barcode, is("657670342075"));
    assertThat(uprooted.status, is("Available"));
    assertThat(uprooted.instanceId, is(notNullValue()));
    assertThat(uprooted.materialTypeId, is(bookMaterialTypeId));
    assertThat(uprooted.permanentLoanTypeId, is(canCirculateLoanTypeId));
    assertThat(uprooted.permanentLocationId, is(mainLibraryLocationId));
    assertThat(uprooted.temporaryLocationId, is(annexLibraryLocationId));
  }

  @Test
  public void anItemCanBeAddedWithAnId()
    throws InterruptedException, ExecutionException, TimeoutException {

    CompletableFuture<Item> addFinished = new CompletableFuture<>();

    String itemId = UUID.randomUUID().toString();

    Item itemWithId = smallAngryPlanet.copyWithNewId(itemId);

    collection.add(itemWithId, succeed(addFinished), fail(addFinished));

    Item added = getOnCompletion(addFinished);

    assertThat(added.id, is(itemId));
  }

  @Test
  public void anItemCanBeUpdated()
    throws InterruptedException, ExecutionException, TimeoutException {

    CompletableFuture<Item> addFinished = new CompletableFuture<>();

    collection.add(smallAngryPlanet, succeed(addFinished), fail(addFinished));

    Item added = getOnCompletion(addFinished);

    CompletableFuture<Void> updateFinished = new CompletableFuture<>();

    Item changed = added.changeStatus("Checked Out");

    collection.update(changed, succeed(updateFinished),
      fail(updateFinished));

    waitForCompletion(updateFinished);

    CompletableFuture<Item> gotUpdated = new CompletableFuture<>();

    collection.findById(added.id, succeed(gotUpdated),
      fail(gotUpdated));

    Item updated = getOnCompletion(gotUpdated);

    assertThat(updated.id, is(added.id));
    assertThat(updated.title, is(added.title));
    assertThat(updated.barcode, is(added.barcode));
    assertThat(updated.permanentLocationId, is(added.permanentLocationId));
    assertThat(updated.temporaryLocationId, is(added.temporaryLocationId));
    assertThat(updated.materialTypeId, is(added.materialTypeId));
    assertThat(updated.permanentLoanTypeId, is(added.permanentLoanTypeId));
    assertThat(updated.status, is("Checked Out"));
  }

  @Test
  public void anItemCanBeDeleted()
    throws InterruptedException, ExecutionException, TimeoutException {

    addSomeExamples(collection);

    CompletableFuture<Item> itemToBeDeletedFuture = new CompletableFuture<>();

    collection.add(temeraire(), succeed(itemToBeDeletedFuture),
      fail(itemToBeDeletedFuture));

    Item itemToBeDeleted = itemToBeDeletedFuture.get();

    CompletableFuture<Void> deleted = new CompletableFuture<>();

    collection.delete(itemToBeDeleted.id,
      succeed(deleted), fail(deleted));

    waitForCompletion(deleted);

    CompletableFuture<Item> findFuture = new CompletableFuture<>();

    collection.findById(itemToBeDeleted.id, succeed(findFuture),
      fail(findFuture));

    assertThat(findFuture.get(), is(nullValue()));

    CompletableFuture<MultipleRecords<Item>> findAllFuture = new CompletableFuture<>();

    collection.findAll(PagingParameters.defaults(), succeed(findAllFuture),
      fail(findAllFuture));

    MultipleRecords<Item> wrappedItems = getOnCompletion(findAllFuture);

    List<Item> allItems = wrappedItems.records;

    assertThat(allItems.size(), is(3));
    assertThat(wrappedItems.totalRecords, is(3));
  }

  @Test
  public void allItemsCanBePaged()
    throws InterruptedException, ExecutionException, TimeoutException {

    WaitForAllFutures<Item> allAdded = new WaitForAllFutures<>();

    collection.add(smallAngryPlanet, allAdded.notifySuccess(), v -> {});
    collection.add(nod, allAdded.notifySuccess(), v -> {});
    collection.add(uprooted, allAdded.notifySuccess(), v -> {});
    collection.add(temeraire, allAdded.notifySuccess(), v -> {});
    collection.add(interestingTimes, allAdded.notifySuccess(), v -> {});

    allAdded.waitForCompletion();

    CompletableFuture<MultipleRecords<Item>> firstPageFuture = new CompletableFuture<>();
    CompletableFuture<MultipleRecords<Item>> secondPageFuture = new CompletableFuture<>();

    collection.findAll(new PagingParameters(3, 0), succeed(firstPageFuture),
      fail(secondPageFuture));

    collection.findAll(new PagingParameters(3, 3), succeed(secondPageFuture),
      fail(secondPageFuture));

    MultipleRecords<Item> firstPage = getOnCompletion(firstPageFuture);
    MultipleRecords<Item> secondPage = getOnCompletion(secondPageFuture);

    assertThat(firstPage.records.size(), is(3));
    assertThat(secondPage.records.size(), is(2));

    assertThat(firstPage.totalRecords, is(5));
    assertThat(secondPage.totalRecords, is(5));
  }

  @Test
  public void itemsCanBeFoundByByPartialName()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    UnsupportedEncodingException {

    CompletableFuture<Item> firstAddFuture = new CompletableFuture<>();
    CompletableFuture<Item> secondAddFuture = new CompletableFuture<>();
    CompletableFuture<Item> thirdAddFuture = new CompletableFuture<>();

    collection.add(smallAngryPlanet, succeed(firstAddFuture),
      fail(firstAddFuture));
    collection.add(nod, succeed(secondAddFuture),
      fail(secondAddFuture));
    collection.add(uprooted, succeed(thirdAddFuture),
      fail(thirdAddFuture));

    CompletableFuture<Void> allAddsFuture = CompletableFuture.allOf(firstAddFuture,
      secondAddFuture, thirdAddFuture);

    getOnCompletion(allAddsFuture);

    Item addedSmallAngryPlanet = getOnCompletion(firstAddFuture);

    CompletableFuture<MultipleRecords<Item>> findFuture = new CompletableFuture<>();

    collection.findByCql("title=\"*Small Angry*\"", new PagingParameters(10, 0),
      succeed(findFuture), fail(findFuture));

    MultipleRecords<Item> wrappedItems = getOnCompletion(findFuture);

    assertThat(wrappedItems.records.size(), is(1));
    assertThat(wrappedItems.totalRecords, is(1));

    assertThat(wrappedItems.records.stream().findFirst().get().id, is(addedSmallAngryPlanet.id));
  }

  @Test
  public void itemsCanBeFoundByBarcode()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    UnsupportedEncodingException {

    CompletableFuture<Item> firstAddFuture = new CompletableFuture<>();
    CompletableFuture<Item> secondAddFuture = new CompletableFuture<>();
    CompletableFuture<Item> thirdAddFuture = new CompletableFuture<>();

    collection.add(smallAngryPlanet, succeed(firstAddFuture),
      fail(firstAddFuture));
    collection.add(nod, succeed(secondAddFuture),
      fail(secondAddFuture));
    collection.add(uprooted, succeed(thirdAddFuture),
      fail(thirdAddFuture));

    CompletableFuture<Void> allAddsFuture = CompletableFuture.allOf(
      firstAddFuture, secondAddFuture, thirdAddFuture);

    getOnCompletion(allAddsFuture);

    Item addedSmallAngryPlanet = getOnCompletion(firstAddFuture);

    CompletableFuture<MultipleRecords<Item>> findFuture = new CompletableFuture<>();

    collection.findByCql("barcode=036000291452", new PagingParameters(10, 0),
      succeed(findFuture), fail(findFuture));

    MultipleRecords<Item> wrappedItems = getOnCompletion(findFuture);

    assertThat(wrappedItems.records.size(), is(1));
    assertThat(wrappedItems.totalRecords, is(1));

    assertThat(wrappedItems.records.stream().findFirst().get().id, is(addedSmallAngryPlanet.id));
  }

  @Test
  public void anItemCanBeFoundById()
    throws InterruptedException, ExecutionException, TimeoutException {

    CompletableFuture<Item> firstAddFuture = new CompletableFuture<>();
    CompletableFuture<Item> secondAddFuture = new CompletableFuture<>();

    collection.add(smallAngryPlanet, succeed(firstAddFuture),
      fail(firstAddFuture));

    collection.add(nod, succeed(secondAddFuture),
      fail(secondAddFuture));

    Item addedItem = getOnCompletion(firstAddFuture);
    Item otherAddedItem = getOnCompletion(secondAddFuture);

    CompletableFuture<Item> findFuture = new CompletableFuture<>();
    CompletableFuture<Item> otherFindFuture = new CompletableFuture<>();

    collection.findById(addedItem.id, succeed(findFuture),
      fail(findFuture));

    collection.findById(otherAddedItem.id, succeed(otherFindFuture),
      fail(otherFindFuture));

    Item foundItem = getOnCompletion(findFuture);
    Item otherFoundItem = getOnCompletion(otherFindFuture);

    assertThat(foundItem, notNullValue());
    assertThat(foundItem.title, is("Long Way to a Small Angry Planet"));
    assertThat(foundItem.instanceId, is(smallAngryPlanet.instanceId));
    assertThat(foundItem.barcode, is("036000291452"));
    assertThat(foundItem.status, is("Available"));
    assertThat(foundItem.materialTypeId, is(bookMaterialTypeId));
    assertThat(foundItem.permanentLoanTypeId, is(canCirculateLoanTypeId));
    assertThat(foundItem.permanentLocationId, is(mainLibraryLocationId));
    assertThat(foundItem.temporaryLocationId, is(annexLibraryLocationId));

    assertThat(otherFoundItem, notNullValue());
    assertThat(otherFoundItem.title, is("Nod"));
    assertThat(otherFoundItem.instanceId, is(nod.instanceId));
    assertThat(otherFoundItem.barcode, is("565578437802"));
    assertThat(otherFoundItem.status, is("Available"));
    assertThat(otherFoundItem.materialTypeId, is(bookMaterialTypeId));
    assertThat(otherFoundItem.permanentLoanTypeId, is(canCirculateLoanTypeId));
    assertThat(otherFoundItem.permanentLocationId, is(mainLibraryLocationId));
    assertThat(otherFoundItem.temporaryLocationId, is(annexLibraryLocationId));
  }

  private void addSomeExamples(ItemCollection itemCollection)
    throws InterruptedException, ExecutionException, TimeoutException {

    WaitForAllFutures<Item> allAdded = new WaitForAllFutures<>();

    itemCollection.add(smallAngryPlanet, allAdded.notifySuccess(), v -> { });
    itemCollection.add(nod, allAdded.notifySuccess(), v -> { });
    itemCollection.add(uprooted, allAdded.notifySuccess(), v -> { });

    allAdded.waitForCompletion();
  }

  private Item smallAngryPlanet() {
    return new Item(null, "Long Way to a Small Angry Planet", "036000291452",
      UUID.randomUUID().toString(), "Available", bookMaterialTypeId,
      mainLibraryLocationId, annexLibraryLocationId, canCirculateLoanTypeId, null);
  }

  private Item nod() {
    return new Item(null, "Nod", "565578437802",
      UUID.randomUUID().toString(), "Available", bookMaterialTypeId,
      mainLibraryLocationId, annexLibraryLocationId, canCirculateLoanTypeId, null);
  }

  private Item uprooted() {
    return new Item(null, "Uprooted", "657670342075",
      UUID.randomUUID().toString(), "Available", bookMaterialTypeId,
      mainLibraryLocationId, annexLibraryLocationId, canCirculateLoanTypeId, null);
  }

  private Item temeraire() {
    return new Item(null, "Temeraire", "232142443432",
      UUID.randomUUID().toString(), "Available", bookMaterialTypeId,
      mainLibraryLocationId, annexLibraryLocationId, canCirculateLoanTypeId, null);
  }

  private Item interestingTimes() {
    return new Item(null, "Interesting Times", "56454543534",
      UUID.randomUUID().toString(), "Available", bookMaterialTypeId,
      mainLibraryLocationId, annexLibraryLocationId, canCirculateLoanTypeId, null);
  }

  private Item getItem(List<Item> allItems, String title) {
    return allItems.stream()
      .filter(it -> StringUtils.equals(it.title, title))
      .findFirst().orElse(null);
  }
}
