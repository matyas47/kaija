// PresetBank.sc
// Instrument-agnostic named preset storage.
// Stores snapshots as a Dictionary of name -> state Dictionary.
// Serialises to/from a simple JSON format using a built-in parser.
// No external quarks required.
//
// Usage:
//   bank = PresetBank.new;
//   bank.save("my preset", (ratio: 1.5, index: 3.0));
//   bank.load("my preset");  // -> (ratio: 1.5, index: 3.0)
//   bank.writeToFile("/path/to/presets.json");
//   bank.readFromFile("/path/to/presets.json");

PresetBank {

	var <presets;   // Dictionary of name -> state Dictionary
	var onChange;   // Function called whenever bank is modified

	*new {
		^super.new.init
	}

	init {
		presets  = Dictionary.new;
		onChange = nil;
		^this
	}

	// ------------------------------------------------------------------
	// Preset management
	// ------------------------------------------------------------------

	save { |name, dict|
		presets[name] = dict.copy;
		this.prNotify;
	}

	load { |name|
		^presets[name]
	}

	delete { |name|
		presets.removeAt(name);
		this.prNotify;
	}

	rename { |oldName, newName|
		var state;
		if(presets[oldName].notNil) {
			state = presets[oldName];
			presets.removeAt(oldName);
			presets[newName] = state;
			this.prNotify;
		};
	}

	includes { |name|
		^presets.includesKey(name)
	}

	names {
		^presets.keys.asArray.sort
	}

	size {
		^presets.size
	}

	// ------------------------------------------------------------------
	// File I/O
	// ------------------------------------------------------------------

	writeToFile { |path|
		var json, file;
		("PresetBank: writing " ++ presets.size ++ " presets to " ++ path).postln;
		if(presets.size == 0) { "PresetBank: no presets to save".warn; ^false };
		json = this.prSerialise(presets);
		file = File(path, "w");
		if(file.isOpen) {
			file.write(json);
			file.close;
			("PresetBank: wrote " ++ path).postln;
			^true
		} {
			("PresetBank: could not open file for writing: " ++ path).warn;
			^false
		};
	}

	readFromFile { |path|
		var text, file, parsed, snap;
		("PresetBank: reading from " ++ path).postln;
		file = File(path, "r");
		if(file.isOpen.not) { ("PresetBank: could not read " ++ path).warn; ^false };
		text = file.readAllString;
		file.close;
		("PresetBank: read " ++ text.size ++ " chars").postln;
		parsed = this.prParse(text);
		if(parsed.isNil) { "PresetBank: parse failed".warn; ^false };
		("PresetBank: parsed " ++ parsed.size ++ " presets").postln;
		presets = Dictionary.new;
		parsed.keysValuesDo({ |name, dict|
			snap = Dictionary.new;
			dict.keysValuesDo({ |k, v| snap[k.asSymbol] = v });
			presets[name] = snap;
		});
		this.prNotify;
		^true
	}

	// ------------------------------------------------------------------
	// Callbacks
	// ------------------------------------------------------------------

	onChanged { |func|
		onChange = func;
	}

	prNotify {
		if(onChange.notNil) { onChange.value(this.names) };
	}

	// ------------------------------------------------------------------
	// JSON serialiser
	// ------------------------------------------------------------------

	prSerialise { |obj|
		var pairs, result;
		if(obj.isKindOf(Dictionary)) {
			pairs = [];
			obj.keysValuesDo({ |k, v|
				pairs = pairs.add(this.prQuote(k.asString) ++ ":" ++ this.prSerialise(v));
			});
			^"{" ++ pairs.join(",") ++ "}"
		};
		if(obj.isKindOf(Array)) {
			^"[" ++ obj.collect({ |x| this.prSerialise(x) }).join(",") ++ "]"
		};
		if(obj.isKindOf(String))  { ^this.prQuote(obj) };
		if(obj.isKindOf(Symbol))  { ^this.prQuote(obj.asString) };
		if(obj.isKindOf(Boolean)) { ^if(obj) { "true" } { "false" } };
		if(obj.isNil)             { ^"null" };
		^obj.asString
	}

	prQuote { |str|
		^"\"" ++ str.replace("\\", "\\\\").replace("\"", "\\\"") ++ "\""
	}

	// ------------------------------------------------------------------
	// JSON parser
	// Uses a single shared position variable passed through closures.
	// All var declarations are at the top of the method.
	// ------------------------------------------------------------------

	prParse { |text|
		var pos, skipWS, parseString, parseNumber, parseArray, parseObject, parseValue;

		pos = 0;

		skipWS = {
			while({ pos < text.size and: { text[pos].isSpace } }) { pos = pos + 1 };
		};

		parseString = {
			var result, ch;
			result = "";
			pos = pos + 1;
			while({ pos < text.size and: { text[pos] != $" } }) {
				ch = text[pos];
				if(ch == $\\) {
					pos = pos + 1;
					if(pos < text.size) { result = result ++ text[pos].asString };
				} {
					result = result ++ ch.asString;
				};
				pos = pos + 1;
			};
			pos = pos + 1;
			result
		};

		parseNumber = {
			var start;
			start = pos;
			if(pos < text.size and: { text[pos] == $- }) { pos = pos + 1 };
			while({ pos < text.size and: { "0123456789.eE+-".includes(text[pos]) } }) {
				pos = pos + 1;
			};
			text.copyRange(start, pos - 1).asFloat
		};

		parseArray = {
			var arr;
			arr = [];
			pos = pos + 1;
			skipWS.value;
			while({ pos < text.size and: { text[pos] != $] } }) {
				arr = arr.add(parseValue.value);
				skipWS.value;
				if(pos < text.size and: { text[pos] == $, }) { pos = pos + 1 };
				skipWS.value;
			};
			pos = pos + 1;
			arr
		};

		parseObject = {
			var dict, key, val;
			dict = Dictionary.new;
			pos = pos + 1;
			skipWS.value;
			while({ pos < text.size and: { text[pos] != $} } }) {
				skipWS.value;
				key = parseString.value;
				skipWS.value;
				pos = pos + 1;
				skipWS.value;
				val = parseValue.value;
				dict[key] = val;
				skipWS.value;
				if(pos < text.size and: { text[pos] == $, }) { pos = pos + 1 };
				skipWS.value;
			};
			pos = pos + 1;
			dict
		};

		parseValue = {
			var ch;
			skipWS.value;
			if(pos >= text.size) { nil } {
				ch = text[pos];
				if(ch == $")                            { parseString.value  } {
				if(ch == $[)                            { parseArray.value   } {
				if(ch == ${)                            { parseObject.value  } {
				if(ch == $t) { pos = pos + 4; true  } {
				if(ch == $f) { pos = pos + 5; false } {
				if(ch == $n) { pos = pos + 4; nil   } {
				parseNumber.value }}}}}}
			}
		};

		^parseValue.value
	}

}
