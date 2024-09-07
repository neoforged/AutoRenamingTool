# AutoRenamingTool

**AutoRenamingTool**, or **ART**, is a tool for remapping/renaming the contents of a JAR file, with extensibility for
applying other transformations, both built-in and user-provided.

This is the NeoForged successor
to [ForgeAutoRenamingTool or FART](https://github.com/MinecraftForge/ForgeAutoRenamingTool).

## Usage

This tool is available from NeoForged's Maven repository ([maven.neoforged.net][art_maven]), at the Maven coordinates
`net.neoforged:AutoRenamingTool`. Check the Maven repository for the latest versions.

For ordinary command-line usage, use the `all`-classified artifact, which contains all of ART's dependencies shaded in.

The following are the core command-line options (options are optional unless otherwise specified):

- `--input <path>` - Path to the input JAR file; **required**
- `--output <path>` - Path to the output JAR file; if not present, then the input JAR file is _overwritten_ with the
  output
- `--map <path>`/`--names <path>` - Path to the mappings file, which may be of any format supported
  by [SrgUtils][srgutils]. Can be specified multiple times. If more than one mappings file is specified, the rest will
  be merged with the first one sequentially, discarding any entries already present.
- `--reverse` - When present, any provided mappings are first reversed (`A -> B` becomes `B -> A`) before its
  application
- `--log <path>` - Path to an output file for logging; if not present, then logging is directed to the
  console (`System.out`)
- `--lib <path>`/`-e <path` - Path to a library JAR file, used for inheritance calculations
- `--threads <num>` - The amount of threads to use for parallel processing; if not present, defaults to the amount of
  processors

Command-line options are also provided for various additional built-in transformations included by ART.

### Built-in transformation

ART provides certain additional built-in transformations, which are useful in correcting issues in the original code
(commonly those made by [ProGuard][proguard]) or cleaning up certain attributes or bytecode. The following are
command-line options for controlling these transformations.

- `--ann-fix` - Fixes parameter annotation offsets in constructors with synthetic parameters caused by ProGuard

  In ProGuard versions older than 6.1, the parameter annotations attributes are erroneously modified for
  constructors which have synthetic parameters, such as enum constructors or constructors for inner (non-static)
  classes. This caused parameter annotations on source-declared parameters to be 'shifted' towards those synthetic
  parameters.

  For example, given an enum constructor of `EnumValue(String name, int ordinal, @Nonnegative short a, short b,
  @Nullable Object c)` (where the `String` and `int` parameters are synthetic ones inserted by the compiler for
  enums), this issue would cause the constructor to be effectively written out by ProGuard as `EnumValue(@Nonnegative
  String name, int ordinal, @Nullable short a, short b, Object c)` -- the parameter annotations are shifted over by two
  places, corresponding to the two synthetic parameters.

  By enabling this option, ART will detect constructors for enums and inner classes, and fix the problem if it exists.

- `--record-fix` - Fixes certain record issues done by ProGuard

  Certain issues may occur with records when processed by ProGuard:

    - For ProGuard versions older than 7.4, the [`Record` attribute][record_attr] is erroneously removed for empty
      records (records with zero components).
    - ProGuard may accidentally raise the visibility of record fields from `private`.

  By enabling this option, ART will detect and fix raised-visibility record fields and missing `Record` attributes for
  empty records.

- `--ids-fix [config]` - Renames source-invalid local variable identifiers

  This option configures a transformer to fix source-invalid local variable identifiers by renaming them to an
  autogenerated name.

  The optional parameter may be of two values:

    - `ALL` _(default)_ - All source-invalid local variable identifiers are renamed.
    - `SNOWMEN` - Only local variable identifiers beginning with the snowman character (U+2603, `☃`), which was used for
      Minecraft versions since ~1.8 up to 1.17.

- `--src-fix [config]` - Renames source file names according to configured parameter

  This option configures a transformer to rename the source file name stored in the [`SourceFile`
  attributes][sourcefile_attr], according to a renaming strategy configured by an optional parameter.

  The optional parameter may be of one value (more values may be added in the future by public contributions):

    - `JAVA` _(default)_ - Source files get the name of the outermost class (even for inner/nested classes)

- `--strip-sigs [config]`

  This option configures a transformer to remove certain signature files and signature manifest entries from a
  [signed JAR][signed_jar].

  The optional parameter may be of one value (more values may be added in the future by public contributions):

    - `ALL` _(default)_ - All signature files and manifest entries are removed.

- `--ff-line-numbers <path>` - Remaps source line numbers using [ForgeFlower][forgeflower] line mapping information

  This option configures a transformer to remap the line numbers in the bytecode according to the line mapping
  information stored in a source JAR created by the ForgeFlower decompiler and derivatives (specified by the required
  parameter to this option). This allows compiled JARs to have matching line numbers with the decompiled source JAR
  emitted by ForgeFlower.

  The line mapping information is stored in the 'extra' field of the file header of each source file, as a header with
  ID of `0x4646`, or `FF` in text form. After the ID and length of the header, the data is structured as follows:

    - The code version of the line mapping information, which is `1` as of writing (any newer code version will cause an
      exception), stored as a byte;
    - 0 or more line mapping entries, stored as four bytes: two bytes for the original line number, and two bytes for
      the new line number. The amount of line mapping entries can be calculated based off the length of the header (in
      bytes): `(length - 1) / 4` (1 byte for the code version, 4 bytes for each entry).

  A line number is remapped according to the line mapping entry whose original line number is equal or greater than that
  line number. Classes which lack corresponding line mapping information will not have their line numbers modified.

- `--disable-abstract-param` - Disables writing out abstract parameter naming information file to the output JAR

  Ordinarily, `abstract` (and `native`) methods do not store the names of their parameters, because the
  [`LocalVariableTable` attribute][lvt_attr] is only written out for code-bearing methods and the [`MethodParameters`
  attribute][methodparams_attr] is only written out when the compiler is configured to do so with `-parameters`.

  To allow for other programs, particularly the ForgeFlower decompiler and derivatives, to provide parameter names
  for `abstract` methods, ART includes the `fernflower_abstract_parameter_names.txt` file in the root directory of the
  output JAR, which contains the parameter names of `abstract` methods which have at least one parameter. (If the file
  would be empty because there are no matching methods, then the file is not written out.)

  The file is formatted such that each line contains one method, which is a space-separated list containing the
  following: the class name, the method name, the method descriptor, and the names of parameters separated by spaces.

  Because the parameter names are not present in the bytecode, the parameter names are synthesized in the format of
  `var#`, where `#` is the local variable slot of the parameter. The parameter names are also remapped before being
  stored in the file.

  This option disables this feature, preventing the `fernflower_abstract_parameter_names.txt` from being written by ART.

- `--unfinal-params`
  
  Removes the final attribute from method parameters.

## License

ART is licensed under the GNU Lesser General Public License, version 2.1. See the `LICENSE` file for the full license.

[art_maven]: https://maven.neoforged.net/#/releases/net/neoforged/AutoRenamingTool

[srgutils]: https://github.com/neoforged/SrgUtils

[proguard]: https://www.guardsquare.com/proguard

[record_attr]: https://docs.oracle.com/javase/specs/jvms/se21/html/jvms-4.html#jvms-4.7.30

[sourcefile_attr]: https://docs.oracle.com/javase/specs/jvms/se21/html/jvms-4.html#jvms-4.7.10

[signed_jar]: https://docs.oracle.com/en/java/javase/21/docs/specs/jar/jar.html#signed-jar-file

[forgeflower]: https://github.com/MinecraftForge/ForgeFlower

[lvt_attr]: https://docs.oracle.com/javase/specs/jvms/se21/html/jvms-4.html#jvms-4.7.13

[methodparams_attr]: https://docs.oracle.com/javase/specs/jvms/se21/html/jvms-4.html#jvms-4.7.24
