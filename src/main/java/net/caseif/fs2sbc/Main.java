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

        writeName(output, dir);
        try (DirectoryStream<Path> directoryStream = Files.newDirectoryStream(dir)) {
            for (Path path : directoryStream) {
                try {
                    if (Files.isDirectory(path)) {
                        output.write(Tag.GROUP);
                        writeDirectory(output, path);
                        output.write(Tag.END);
                    } else {
                        writeFile(output, path);
                    }
                } catch (IOException ex) {
                    ex.printStackTrace();
                    die("Encountered IOException while writing file " + path.toString());
                }
            }
        }
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

    private static void die(String msg) {
        System.err.println(msg);
        System.exit(1);
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
                case "-i":
                    if (i + 1 == args.length || args[i + 1].startsWith("-")) {
                        die("Parameter required for input flag");
                    }
                    setFlag(new CommandFlag<>(CommandFlag.Type.INPUT, args[i + 1]));
                    i++;
                    break;
                case "-o":
                    if (i + 1 == args.length || args[i + 1].startsWith("-")) {
                        die("Parameter required for output flag");
                    }
                    setFlag(new CommandFlag<>(CommandFlag.Type.OUTPUT, args[i + 1]));
                    i++;
                    break;
                case "-b":
                    setFlag(new CommandFlag<>(CommandFlag.Type.BASE64));
                    break;
                case "-v":
                    setFlag(new CommandFlag<>(CommandFlag.Type.VERBOSE));
                    break;
                default:
                    die("Invalid parameter \"" + args[i] + "\"");
            }
        }
    }

    private static void validateParameters() {
        if (!flags.keySet().contains(CommandFlag.Type.INPUT) || !flags.keySet().contains(CommandFlag.Type.OUTPUT)) {
            die("Input and output flags are required");
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

}
