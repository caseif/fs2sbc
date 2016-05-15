# fs2sbc
**fs2sbc** is a command-line utility written in Java for packaging filesystem resources into
[SBC](https://github.com/caseif/SBC) blobs.

## Usage
`java -jar fs2sbc.jar [-b64 | -b91] [-v] -i /path/to/input_dir -o /path/to/output.sbc`

### Flags
| Flag | Description
|---|---
| -i | Path to input directory or file (resource to package)
| -o | Path to output file (byte blob will be written here
| -b64 | Specifies output to be written as base64 string
| -b91 | Specifies output to be written as a base91 string (more compact, but less widely supported)
| -v | Specifies verbose output to be written to stdout
