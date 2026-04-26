PitchView {

	classvar <pitchClasses;

	*initClass {
		pitchClasses = [
			"C","C#","D","D#","E","F",
			"F#","G","G#","A","A#","B"
		];
	}

	*hzToMidi { |hz|
		if(hz.isNil or: { hz <= 0 }) { ^nil };
		^hz.cpsmidi;
	}

	*nearestMidi { |hz|
		^this.hzToMidi(hz).round;
	}

	*pitchClass { |midi|
		^pitchClasses[midi % 12];
	}

	*octave { |midi|
		^(midi.div(12) - 1);
	}

	*deviationCents { |hz, precision = 0.1|
		var midi, nearest;
		midi = this.hzToMidi(hz);
		nearest = midi.round;
		^((midi - nearest) * 100).round(precision);
	}

	*hzToPitchString { |hz, precision = 0.1|
		var midi, nearest, pc, oct, dev, sign;

		if(hz.isNil or: { hz <= 0 }) { ^"nil" };

		midi = this.hzToMidi(hz);
		nearest = midi.round.asInteger;

		pc = this.pitchClass(nearest);
		oct = this.octave(nearest);

		dev = ((midi - nearest) * 100).round(precision);

		sign = if(dev >= 0) { "+" } { "" };

		^(pc ++ oct.asString ++ " " ++ sign ++ dev.asString ++ "c");
	}

	*hzToData { |hz|
		var midi, nearest;

		if(hz.isNil or: { hz <= 0 }) { ^nil };

		midi = this.hzToMidi(hz);
		nearest = midi.round.asInteger;

		^(
			hz: hz,
			midi: midi,
			nearestMidi: nearest,
			pitchClass: this.pitchClass(nearest),
			octave: this.octave(nearest),
			deviationCents: (midi - nearest) * 100
		);
	}

}
