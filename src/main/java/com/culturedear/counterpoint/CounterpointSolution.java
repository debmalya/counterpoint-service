package com.culturedear.counterpoint;

import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by jamesweaver on 9/25/15.
 */
public class CounterpointSolution {
  // Default beats per measure
  private static int beatsPerMeasure = 4;

  // Default beats per measure.
  // TODO: Utilize this
  private static int beatType = 4;

  // Onset units per measure
  // TODO: Calculate as a function of 8 (eighth notes), beatsPerMeasure and beatType
  private static int onsetUnitsPerMeasure = 8;

  // First dimension is voice #, second is array of notes
  private List<List<Note>> notesAllVoices = new ArrayList<List<Note>>();

  public void addVoice(List<Note> notes) {
    notesAllVoices.add(notes);
  }

  public ScorePartwise toScorePartwise() {
    Measure curMeasure = new Measure(" ");
    ScorePartwise scorePartwise = new ScorePartwise();
    if (notesAllVoices.size() > 0) {
      scorePartwise.getScoreParts().add(new ScorePart("Cantus firmus", "P1"));
      for (int voiceIdxA = 1; voiceIdxA < notesAllVoices.size(); voiceIdxA++) {
        scorePartwise.getScoreParts().add(new ScorePart("Melody " + voiceIdxA, "P" + (voiceIdxA + 1)));
      }

      for (int voiceIdxB = 0; voiceIdxB < notesAllVoices.size(); voiceIdxB++) {
        Part part = new Part("P" + (voiceIdxB + 1));
        scorePartwise.getParts().add(part);

        List<Note> notesForPart = notesAllVoices.get(voiceIdxB);

        // Iteratate over the notes in this part and insert Measure elements
        Iterator<Note> noteIterator = notesForPart.iterator();

        while (noteIterator.hasNext()) {
          Note note = noteIterator.next();
          int onset = note.getOnset();
          if (onset % onsetUnitsPerMeasure == 0) {
            curMeasure = new Measure("" + (onset / onsetUnitsPerMeasure + 1));
            part.getMeasures().add(curMeasure);
          }
          curMeasure.getNotes().add(note);
        }
      }
    }

    // Iterate over the measures for all parts, identifying the first chord in each measure
    boolean keepGoing = true;
    int measureNum = 0;
    while (keepGoing) {
      //System.out.println("Analyzing chord for measure " + measureNum);
      Note topMeasureFirstNote = null;
      List<Note> chordNotes = new ArrayList();
      List<Part> parts = scorePartwise.getParts();
      Iterator<Part> partIterator = parts.iterator();
      while (partIterator.hasNext()) {
        Part part = partIterator.next();
        List<Measure> measures = part.getMeasures();
        if (measureNum < measures.size()) {
          Measure measure = measures.get(measureNum);
          List<Note> notes = measure.getNotes();
          if (notes.size() > 0) {
            Note firstNoteInMeasure = notes.get(0);
            if (topMeasureFirstNote == null) {
              topMeasureFirstNote = firstNoteInMeasure;
            }
            //System.out.println("- note: " + firstNoteInMeasure.getPitch());
            chordNotes.add(firstNoteInMeasure);
          }
          else {
            // Unexpected, as all measures should have at least one note
            keepGoing = false;
          }
        }
        else {
          // We've run out of measures in at least one part
          keepGoing = false;
        }
      }
      if (topMeasureFirstNote != null) {
        Collections.sort(chordNotes);

        String notesString = chordNotes.stream()
            .map(note -> note.getPitch().toString())
            .collect(Collectors.joining(" "));

        // Call the Chord Analyzer service
        RestTemplate restTemplate = new RestTemplate();
        Lyric lyric = null;
        // TODO: Identify best practice (e.g. env variable, application.properties, yaml) for representing the URL for the following service
        try {
          ClientMusicChord chord = restTemplate.getForObject("http://chordanalyzerservice.cfapps.pez.pivotal.io/analyze?notes=" + notesString, ClientMusicChord.class);

          // TODO: Remove this temporary fix of stripping the octave number, when Class#getRoot method no longer returns octave number
          String chordRootStr = chord.getRoot().toString();
          if (chordRootStr.length() > 1) {
            chordRootStr = chordRootStr.substring(0, chordRootStr.length() - 1);
          }

          // TODO: Remove this temporary fix of making the flat sign lower case, when Class#getRoot method no longer returns a "B" instead of "b"
          if (chordRootStr.length() > 1) {
            chordRootStr = chordRootStr.substring(0, chordRootStr.length() - 1) + chordRootStr.substring(1).toLowerCase();
          }

          lyric = new Lyric(chordRootStr + " " + chord.getChordType().toLowerCase());
        }
        catch (Exception e) {
          System.out.println("Caught exception when analyzing chord " + e);
          //lyric = new Lyric("???");
        }
        topMeasureFirstNote.setLyric(lyric);
      }
      measureNum++;
    }

    return scorePartwise;
  }

  @Override
  public String toString() {
    return "CounterpointSolution{" +
        "notesAllVoices=" + notesAllVoices +
        '}';
  }
}
