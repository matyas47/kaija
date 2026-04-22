// KaijaScale.sc
// Scala microtonal scale loader and MIDI-to-Hz mapper for Kaija.
//
// Loads .scl files (Scala scale format) and maps MIDI note numbers
// to frequencies in Hz. Defaults to 12TET if no scale is loaded.
//
// Untuned MIDI notes (those that fall between scale degrees) are
// mapped to the nearest scale degree rather than silenced or
// passed through as 12TET.
//
// Usage:
//   scale = KaijaScale.new;
//   scale.loadFile("/path/to/scale.scl");
//   freq = scale.freqAt(60);   // middle C in current tuning
//   scale.clear;               // revert to 12TET

KaijaScale {

	var scaleName;       // name string from .scl file
	var scaleRatios;     // Array of ratios (Float) for one octave, including 2/1
	var rootMidi;        // MIDI note number treated as the scale root (default 60)
	var rootHz;          // Hz of the root note (default 261.626 = middle C)
	var loaded;          // Boolean — true if a .scl file is loaded

	*new { |rootMidi=60, rootHz=261.626|
		^super.new.init(rootMidi, rootHz)
	}

	init { |rm, rh|
		rootMidi    = rm.asInteger;
		rootHz      = rh.asFloat;
		loaded      = false;
		scaleName   = "12TET";
		scaleRatios = nil;
		^this
	}

	// ------------------------------------------------------------------
	// File loading
	// ------------------------------------------------------------------

	// Load a .scl file from path.
	// Returns true on success, false on failure.
	loadFile { |path|
		var file, lines, ratios, count;

		file = File(path, "r");
		if(file.isOpen.not) {
			("KaijaScale: could not open file: " ++ path).warn;
			^false
		};

		lines = [];
		file.do({ |line| lines = lines.add(line.stripWhiteSpace) });
		file.close;

		// Parse .scl format:
		// - Lines beginning with ! are comments
		// - First non-comment line is the scale name
		// - Second non-comment line is the note count
		// - Remaining non-comment lines are pitches (ratios or cents)
		lines = lines.select({ |l| l.notEmpty and: { l[0] != $! } });

		if(lines.size < 3) {
			"KaijaScale: file too short to be a valid .scl file.".warn;
			^false
		};

		scaleName = lines[0];
		count     = lines[1].asInteger;

		if(lines.size < (count + 2)) {
			"KaijaScale: note count does not match number of pitch lines.".warn;
			^false
		};

		ratios = lines.copyRange(2, count + 1).collect({ |line|
			this.prParsePitchLine(line)
		});

		if(ratios.includes(nil)) {
			"KaijaScale: failed to parse one or more pitch lines.".warn;
			^false
		};

		// Ensure 2/1 (octave) is the last entry
		if(ratios.last.round(0.0001) != 2.0) {
			ratios = ratios.add(2.0)
		};

		scaleRatios = ratios;
		loaded      = true;

		("KaijaScale: loaded '" ++ scaleName ++ "' (" ++ (scaleRatios.size - 1) ++ " notes per octave).").postln;
		^true
	}

	// Revert to 12TET
	clear {
		loaded      = false;
		scaleName   = "12TET";
		scaleRatios = nil;
	}

	// ------------------------------------------------------------------
	// MIDI to Hz mapping
	// ------------------------------------------------------------------

	// Return the frequency in Hz for a given MIDI note number.
	freqAt { |midi|
		if(loaded.not) {
			// 12TET
			^(rootHz * (2 ** ((midi - rootMidi) / 12.0)))
		};
		^this.prScaleFreqAt(midi)
	}

	// ------------------------------------------------------------------
	// Accessors
	// ------------------------------------------------------------------

	name        { ^scaleName }
	isLoaded    { ^loaded }
	numDegrees  { ^if(loaded) { scaleRatios.size - 1 } { 12 } }

	// ------------------------------------------------------------------
	// Private
	// ------------------------------------------------------------------

	// Map a MIDI note to Hz using the loaded scale.
	// Notes between scale degrees bend to nearest degree.
	prScaleFreqAt { |midi|
		var semitoneOffset, octave, degree, degreeCount, degreeFloat;
		var loDegree, hiDegree, loRatio, hiRatio, ratio;

		degreeCount    = scaleRatios.size - 1;  // exclude octave entry
		semitoneOffset = midi - rootMidi;

		// Map semitone offset to fractional scale degree
		degreeFloat = semitoneOffset * (degreeCount / 12.0);

		// Split into octave and degree
		octave    = degreeFloat.div(degreeCount);
		degree    = degreeFloat - (octave * degreeCount);

		// Handle negative offsets
		if(degree < 0) {
			degree = degree + degreeCount;
			octave = octave - 1;
		};

		// Find bounding scale degrees and interpolate to nearest
		loDegree = degree.floor.asInteger;
		hiDegree = degree.ceil.asInteger;

		loRatio = if(loDegree == 0) { 1.0 } { scaleRatios[loDegree - 1] };
		hiRatio = scaleRatios[hiDegree.min(degreeCount) - 1] ? scaleRatios.last;

		// Snap to nearest degree
		ratio = if((degree - loDegree) < 0.5) { loRatio } { hiRatio };

		^(rootHz * (2 ** octave) * ratio)
	}

	// Parse a single pitch line from a .scl file.
	// Lines may be ratios (e.g. "3/2") or cents (e.g. "702.0").
	// Returns a Float ratio, or nil on failure.
	prParsePitchLine { |line|
		var parts, cents;

		// Strip inline comments
		line = line.split($!)[0].stripWhiteSpace;

		if(line.includes($\/)) {
			// Ratio format: numerator/denominator
			parts = line.split($\/);
			if(parts.size == 2) {
				^(parts[0].asFloat / parts[1].asFloat)
			};
			^nil
		} {
			// Cents format: convert to ratio
			cents = line.asFloat;
			^(2 ** (cents / 1200.0))
		};
	}

}
