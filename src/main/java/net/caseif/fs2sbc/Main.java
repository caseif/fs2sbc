/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2016, Max Roncace <me@caseif.net>
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package net.caseif.fs2sbc;

import net.caseif.fs2sbc.util.helper.ByteHelper;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

public class Main {

    private static Map<CommandFlag.Type, CommandFlag<?>> flags = new HashMap<>();

    public static void main(String[] args) {
        readParameters(args);
        validateParameters();

        Path input = Paths.get((String) flags.get(CommandFlag.Type.INPUT).getValue());
        if (!Files.exists(input)) {
            die("Input path does not exist");
        }

        if (flags.containsKey(CommandFlag.Type.VERBOSE)) {
            System.out.println("Opening stream to temp file");
        }
        Path tempOut = getTempDirectory().resolve("fs2sbc." + System.currentTimeMillis() + ".tmp");
        OutputStream os = createOutputStream(tempOut);

        writeMagicNumber(os);
        writeBody(input, os);

        writeToTarget(tempOut);
    }

    private static void setFlag(CommandFlag<?> flag) {
        flags.put(flag.getType(), flag);
    }

    private static void writeDirectory(OutputStream output, Path dir) throws IOException {
        assert Files.isDirectory(dir);

        if (flags.containsKey(CommandFlag.Type.VERBOSE)) {
            System.out.println("Processing directory " + dir.toString());
        }

        output.write(Tag.GROUP);
        writeName(output, dir);

        try (DirectoryStream<Path> directoryStream = Files.newDirectoryStream(dir)) {
            for (Path path : directoryStream) {
                try {
                    if (Files.isDirectory(path)) {
                        writeDirectory(output, path);
                    } else {
                        writeFile(output, path);
                    }
                } catch (IOException ex) {
                    ex.printStackTrace();
                    die("Encountered IOException while writing file " + path.toString());
                }
            }
        }

        output.write(Tag.END);
    }

    private static void writeFile(OutputStream output, Path file) throws IOException {
        assert !Files.isDirectory(file);

        if (flags.containsKey(CommandFlag.Type.VERBOSE)) {
            System.out.println("Processing file " + file.toString());
        }

        output.write(Tag.BLOB);
        if (Files.size(file) > Integer.MAX_VALUE) {
            die("Cannot package file " + file.toString() + " - size is greater than Integer.MAX_VALUE");
        }
        output.write(ByteHelper.getBytes((int) Files.size(file)));

        InputStream stream = Files.newInputStream(file);
        byte[] bytes = new byte[(int) Files.size(file)];
        //noinspection ResultOfMethodCallIgnored
        stream.read(bytes);

        output.write(bytes);
        output.flush();
    }

    private static void writeName(OutputStream output, Path file) throws IOException {
        byte[] name = file.getFileName().toString().getBytes(StandardCharsets.UTF_8);
        output.write(ByteHelper.getBytes((short) (name.length & 0xFF)));
        output.write(name);
    }

    private static void die(String msg, boolean printUsage) {
        System.err.println(msg);
        if (printUsage) {
            System.err.println("Pass --help flag for usage information");
        }
        System.exit(1);
    }

    private static void die(String msg) {
        die(msg, false);
    }

    private class Tag {
        private static final byte END = 0x00;
        private static final byte GROUP = 0x01;
        private static final byte BLOB = 0x02;
    }

    private static Path getTempDirectory() {
        return Paths.get(System.getProperty("java.io.tmpdir"));
    }

    private static void readParameters(String[] args) {
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "-?":
                case "--help":
                    displayHelp();
                    System.exit(0);
                    break;
                case "-i":
                case "--input":
                    if (i + 1 == args.length || args[i + 1].startsWith("-")) {
                        die("Parameter required for input flag", true);
                    }
                    setFlag(new CommandFlag<>(CommandFlag.Type.INPUT, args[i + 1]));
                    i++;
                    break;
                case "-o":
                case "--output":
                    if (i + 1 == args.length || args[i + 1].startsWith("-")) {
                        die("Parameter required for output flag", true);
                    }
                    setFlag(new CommandFlag<>(CommandFlag.Type.OUTPUT, args[i + 1]));
                    i++;
                    break;
                case "-b":
                case "--base64":
                    setFlag(new CommandFlag<>(CommandFlag.Type.BASE64));
                    break;
                case "-v":
                case "--verbose":
                    setFlag(new CommandFlag<>(CommandFlag.Type.VERBOSE));
                    break;
                default:
                    die("Invalid parameter \"" + args[i] + "\"", true);
            }
        }
    }

    private static void validateParameters() {
        if (!flags.keySet().contains(CommandFlag.Type.INPUT) || !flags.keySet().contains(CommandFlag.Type.OUTPUT)) {
            die("Input and output flags are required", true);
        }
    }

    private static void writeMagicNumber(OutputStream os) {
        try {
            os.write(new byte[]{(byte) 0xB1, 0x0B, (byte) 0xFE, 0x57});
            os.flush();
        } catch (IOException ex) {
            ex.printStackTrace();
            die("Failed to write to output stream");
        }
    }

    private static void writeBody(Path input, OutputStream os) {
        try {
            if (Files.isDirectory(input)) {
                writeDirectory(os, input);
            } else {
                writeFile(os, input);
            }
            os.flush();
        } catch (IOException ex) {
            ex.printStackTrace();
            die("Failed to write output stream");
        }
    }

    private static void writeToTarget(Path tempOut) {
        try {
            if (flags.containsKey(CommandFlag.Type.VERBOSE)) {
                System.out.println("Copying temp file to destination");
            }

            Path output = Paths.get((String) flags.get(CommandFlag.Type.OUTPUT).getValue());
            OutputStream out = createOutputStream(output);
            InputStream in = Files.newInputStream(tempOut);

            if (flags.containsKey(CommandFlag.Type.BASE64)) {
                System.out.println("Base64 encoding specified - encoding output");
                byte[] buffer = new byte[1200]; // array size must be multiple of 3
                int len = in.read(buffer);
                while (len != -1) {
                    out.write(Base64.getEncoder().encode(buffer), 0, len);
                    len = in.read(buffer);
                }
            } else {
                byte[] buffer = new byte[1024];
                int len = in.read(buffer);
                while (len != -1) {
                    out.write(buffer, 0, len);
                    len = in.read(buffer);
                }
            }
            in.close();

            if (flags.containsKey(CommandFlag.Type.VERBOSE)) {
                System.out.println("Deleting temp file");
            }
            Files.delete(tempOut);
        } catch (IOException ex) {
            ex.printStackTrace();
            die("Failed to copy temp file");
        }
    }

    private static OutputStream createOutputStream(Path path) {
        try {
            Files.deleteIfExists(path);
            Files.createFile(path);
            return Files.newOutputStream(path);
        } catch (IOException ex) {
            ex.printStackTrace();
            die("Failed to create output stream to temp file");
            return null; // never executes
        }
    }

    private static void displayHelp() {
        System.out.println(Main.class.getPackage().getImplementationTitle()
                + " version " + Main.class.getPackage().getImplementationVersion()
                + " by " + Main.class.getPackage().getImplementationVendor());
        System.out.println();

        System.out.println("Usage: java -jar fs2sbc.jar [flags] -i [input file] -o [output file]");
        System.out.println("Parameters:");
        System.out.println("    -i, --input");
        System.out.println("                     Path to input directory or file (resource to package)");
        System.out.println("    -o, --output");
        System.out.println("                     Path to output file (byte blob will be written here)");
        System.out.println("    -b, --base64");
        System.out.println("                     Specifies output to be written as base64 string");
        System.out.println("    -v, --verbose");
        System.out.println("                     Specifies verbose output to be written to stdout");
        System.out.println("    -?, --help");
        System.out.println("                     Displays usage information");
    }

}
