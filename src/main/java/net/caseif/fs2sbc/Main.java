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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

public class Main {

    private static Map<CommandFlag.Type, CommandFlag<?>> flags = new HashMap<>();

    public static void main(String[] args) {
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "-i":
                    if (i + 1 == args.length || args[i + 1].startsWith("-")) {
                        System.err.println("Parameter required for input flag");
                        System.exit(1);
                    }
                    setFlag(new CommandFlag<>(CommandFlag.Type.INPUT, args[i + 1]));
                    i++;
                    break;
                case "-o":
                    if (i + 1 == args.length || args[i + 1].startsWith("-")) {
                        System.err.println("Parameter required for output flag");
                        System.exit(1);
                    }
                    setFlag(new CommandFlag<>(CommandFlag.Type.OUTPUT, args[i + 1]));
                    i++;
                    break;
                case "-b64":
                    setFlag(new CommandFlag<>(CommandFlag.Type.BASE64));
                    break;
                case "-b91":
                    setFlag(new CommandFlag<>(CommandFlag.Type.BASE91));
                    break;
                case "-v":
                    setFlag(new CommandFlag<>(CommandFlag.Type.VERBOSE));
                    break;
                default:
                    System.err.println("Invalid parameter \"" + args[i] + "\"");
                    System.exit(1);
            }
        }

        if (!flags.keySet().contains(CommandFlag.Type.INPUT) || !flags.keySet().contains(CommandFlag.Type.OUTPUT)) {
            System.err.println("Input and output flags are required");
            System.exit(1);
        }
        if (flags.keySet().contains(CommandFlag.Type.BASE64) && flags.keySet().contains(CommandFlag.Type.BASE91)) {
            System.err.println("Cannot specify base64 and base91 encoding simultaneously");
            System.exit(1);
        }

        Path input = Paths.get((String) flags.get(CommandFlag.Type.INPUT).getValue());
        if (!Files.exists(input)) {
            System.err.println("Input path does not exist");
            System.exit(1);
        }

        ByteArrayOutputStream os = new ByteArrayOutputStream();

        try {
            os.write(new byte[]{(byte) 0xB1, 0x0B, (byte) 0xFE, 0x57});
        } catch (IOException ex) {
            ex.printStackTrace();
            System.err.println("Failed to write to output stream");
        }

        try {
            if (Files.isDirectory(input)) {
                writeDirectory(os, input);
            } else {
                writeFile(os, input);
            }
        } catch (IOException ex) {
            ex.printStackTrace();
            System.err.println("Failed to write output stream");
        }

        try {
            if (flags.keySet().contains(CommandFlag.Type.VERBOSE)) {
                System.out.println("Writing output to disk");
            }

            Path output = Paths.get((String) flags.get(CommandFlag.Type.OUTPUT).getValue());
            if (Files.exists(output)) {
                Files.delete(output);
            }
            Files.write(output, os.toByteArray());
        } catch (IOException ex) {
            ex.printStackTrace();
            System.err.println("Failed to write to output file");
        }
    }

    private static void setFlag(CommandFlag<?> flag) {
        flags.put(flag.getType(), flag);
    }

    private static void writeDirectory(ByteArrayOutputStream output, Path dir) throws IOException {
        assert Files.isDirectory(dir);

        if (flags.keySet().contains(CommandFlag.Type.VERBOSE)) {
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
                    System.err.println("Encountered IOException while writing file " + path.toString());
                    System.exit(1);
                }
            }
        }
    }

    private static void writeFile(ByteArrayOutputStream output, Path file) throws IOException {
        assert !Files.isDirectory(file);

        if (flags.keySet().contains(CommandFlag.Type.VERBOSE)) {
            System.out.println("Processing file " + file.toString());
        }

        output.write(Tag.BLOB);
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        if (Files.size(file) > Math.pow(2, 32)) {
            System.err.println("Cannot package file " + file.toString() + " - size is greater than 2^32");
            System.exit(0);
        }
        output.write(ByteHelper.getBytes((int) (Files.size(file) & 0xFFFF)));
        InputStream stream = Files.newInputStream(file);
        int b;
        while ((b = stream.read()) != -1) {
            buffer.write(b);
        }
        output.write(buffer.toByteArray());
    }

    private static void writeName(ByteArrayOutputStream output, Path file) throws IOException {
        byte[] name = file.getFileName().toString().getBytes(StandardCharsets.UTF_8);
        output.write(ByteHelper.getBytes((short) (name.length & 0xFF)));
        output.write(name);
    }

    private class Tag {
        private static final byte END = 0x00;
        private static final byte GROUP = 0x01;
        private static final byte BLOB = 0x02;
    }

}
