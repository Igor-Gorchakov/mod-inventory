package org.folio.inventory.parsing

import org.junit.Test

import java.util.regex.Pattern

class ModsParsingExamples {

  @Test
  void multipleModsRecordCanBeParsedIntoItems() {

    def modsXml = this.getClass()
      .getResourceAsStream('/mods/multiple-example-mods-records.xml')
      .getText("UTF-8")

    def records = new ModsParser(new UTF8LiteralCharacterEncoding())
      .parseRecords(modsXml);

    assert records.size() == 8

    NoTitlesContainEscapedCharacters(records)

    assert records.every( { it.identifiers != null } )
    assert records.every( { it.identifiers.size() > 0 } )

    def california = records.find({
      similarTo(it, "California: its gold and its inhabitants", "69228882")
    })

    assert california != null

    assert california.identifiers.size() == 1
    assert hasIdentifier(california, "UkMaC", "69228882")

    assert california.creators.size() == 1
    hasCreator(california, "Huntley, Henry Veel")

    def studien = records.find({
      similarTo(it, "Studien zur Geschichte der Notenschrift.", "69247446")
    })

    assert studien != null

    assert studien.identifiers.size() == 1
    assert hasIdentifier(studien, "UkMaC", "69247446")

    assert studien.creators.size() == 1
    hasCreator(studien, "Riemann, Karl Wilhelm J. Hugo.")

    def essays = records.find({
      similarTo(it, "Essays on C.S. Lewis and George MacDonald", "53556908")
    })

    assert essays != null

    assert essays.identifiers.size() == 3
    assert hasIdentifier(essays, "UkMaC", "53556908")
    assert hasIdentifier(essays, "StGlU", "b13803414")
    assert hasIdentifier(essays, "isbn", "0889464944")

    assert essays.creators.size() == 1
    hasCreator(essays, "Marshall, Cynthia.")

    def sketches = records.find({
      similarTo(it, "Statistical sketches of Upper Canada", "69077747")
    })

    assert sketches != null

    assert sketches.identifiers.size() == 1
    assert hasIdentifier(sketches, "UkMaC", "69077747")

    assert sketches.creators.size() == 1
    hasCreator(sketches, "Dunlop, William")

    def mcGuire = records.find({
      similarTo(it, "Edward McGuire, RHA", "22169083")
    })

    assert mcGuire != null

    assert hasIdentifier(mcGuire, "isbn", "0716524783")
    assert hasIdentifier(mcGuire, "bnb", "GB9141816")
    assert hasIdentifier(mcGuire, "UkMaC", "22169083")
    assert hasIdentifier(mcGuire, "StEdNL", "1851914")

    assert mcGuire.creators.size() == 1
    hasCreator(mcGuire, "Fallon, Brian.")

    def influenza = records.find({ similarTo(it,
      "Influenza della Poesia sui Costumi", "43620390") })

    assert influenza != null

    assert influenza.identifiers.size() == 1
    assert hasIdentifier(influenza, "UkMaC", "43620390")

    assert influenza.creators.size() == 1
    hasCreator(influenza, "MABIL, Pier Luigi.")

    def nikitovic = records.find({
      similarTo(it, "Pavle Nik Nikitović", "37696876")
    })

    assert nikitovic != null

    assert nikitovic.identifiers.size() == 2
    assert hasIdentifier(nikitovic, "UkMaC", "37696876")
    assert hasIdentifier(nikitovic, "isbn", "8683385124")

    assert nikitovic.creators.size() == 2
    hasCreator(nikitovic, "Nikitović, Pavle")
    hasCreator(nikitovic, "Božović, Ratko.")

    def grammar = records.find({
      similarTo(it, "Grammaire comparée du grec et du latin", "69250051")
    })

    assert grammar != null

    assert grammar.identifiers.size() == 1
    assert hasIdentifier(grammar, "UkMaC", "69250051")

    assert grammar.creators.size() == 2
    hasCreator(grammar, "Riemann, Othon.")
    hasCreator(grammar, "Goelzer, Henri Jules E.")
  }

  private void NoTitlesContainEscapedCharacters(records) {

    assert !records.any {
      Pattern.compile(
        '(\\\\x[0-9a-fA-F]{2})+',
        Pattern.CASE_INSENSITIVE).matcher(it.title).find()
    }
  }

  private boolean similarTo(
    record,
    String expectedSimilarTitle,
    String expectedBarcode) {

      record.title.contains(expectedSimilarTitle) &&
      record.barcode == expectedBarcode
  }

  private boolean hasIdentifier(record, String identifierTypeId, String value) {
    record.identifiers.any({ it.type == identifierTypeId && it.value == value })
  }

  private void hasCreator(Map<String, Object> instance, String name) {
    assert instance.creators.any({ it.name == name })
  }
}


