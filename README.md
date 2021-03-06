# fs2sbc
**fs2sbc** is a command-line utility written in Java for packaging filesystem resources into
[SBC](https://github.com/caseif/SBC) blobs.

## Usage
`java -jar fs2sbc.jar [-b] [-v] -i /path/to/input_dir -o /path/to/output.sbc`

### Flags
| Short Flag | Long Flag | Description
|---|---|---
| -i | --input | Path to input directory or file (resource to package)
| -o | --output | Path to output file (byte blob will be written here)
| -b | --base64 | Specifies output to be written as base64 string
| -v | --verbose | Specifies verbose output to be written to stdout
| -? | --help | Displays usage information

### Planned Features
- Extraction (sbc2fs)
- Built-in `gzip` compression via command flag
- Base91 encoding (tentative)
