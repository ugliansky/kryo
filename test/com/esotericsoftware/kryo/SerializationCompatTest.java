/* Copyright (c) 2016, Martin Grotzke
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following
 * conditions are met:
 *
 * - Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.
 * - Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following
 * disclaimer in the documentation and/or other materials provided with the distribution.
 * - Neither the name of Esoteric Software nor the names of its contributors may be used to endorse or promote products derived
 * from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING,
 * BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT
 * SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE. */

package com.esotericsoftware.kryo;

import com.esotericsoftware.kryo.SerializationCompatTestData.TestData;
import com.esotericsoftware.kryo.io.*;
import com.esotericsoftware.minlog.Log;
import org.objenesis.strategy.StdInstantiatorStrategy;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.nio.ByteOrder;
import java.util.EnumSet;
import java.util.List;

import static com.esotericsoftware.kryo.ReflectionAssert.assertReflectionEquals;

/**
 * Test for serialization compatibility: data serialized with an older version (same major version) must be
 * deserializable with this newer (same major) version. Serialization compatibility is checked for each type
 * that has a default serializer (<code>Kryo.defaultSerializers</code>, populated from Kryo's constructor)
 *
 * Because the various {@link Input}/{@link Output} variants are not compatible, this test is done for each of these
 * variants.
 *
 * This test uses previously created "canonical" tests files (one for each IO variant): it deserializes their content
 * and checks if the deserialized object is equals the expected object. The test files were created once with this
 * test (it writes test files if they're not yet existing), serializing an instance of {@link TestData} that contains
 * fields for each default serializer (respectively the related type) and some arbitratry other fields (just to have a
 * class that's not too trivial).
 *
 * If any of these checks fail it may have different reasons:
 * 1) the serialization format of an IO variant has changed
 * 2) the serialization format of a serializer has changed
 * 3) the {@link TestData} structure/fields have changed
 *
 * In cases 1) and 2) the question is if that was intentionally and if it can't/shouldn't be avoided.
 * If it was intentionally probably kryo's major version should be incremented and new test files must
 * be created (more on that later).
 *
 * In case 3) - assuming that this was intentional - new test files have to be created.
 *
 * To create new test files, just delete the existing ones and run this test. It will write new files
 * so that you only have to commit the changes. Depending on the situation you may consider creating new files
 * from the smallest version of the same major version (e.g. for 3.1.4 this is 3.0.0) - to do this just save
 * this test and the {@link SerializationCompatTest}, go back to the related tag and run the test (there's nothing
 * here to automate creation of test files for a different version).
 */
public class SerializationCompatTest extends KryoTestCase {

    private static final String ENDIANNESS = ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN ? "le" : "be";

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        kryo.setInstantiatorStrategy(new Kryo.DefaultInstantiatorStrategy(new StdInstantiatorStrategy()));
        kryo.setReferences(true);
        kryo.setRegistrationRequired(false);
        // we register EnumSet so that the EnumSet implementation class name is not written: EnumSet implementations
        // have different names e.g. in oracle and ibm jdks (RegularEnumSet vs. MiniEnumSet), therefore the implementation class
        // could not be loaded by a different name. However, with the registered EnumSet, the serializer is used directly according
        // to the registered id.
        kryo.register(EnumSet.class);
    }

    public void testDefaultSerializers() throws Exception {
        Field defaultSerializersField = Kryo.class.getDeclaredField("defaultSerializers");
        defaultSerializersField.setAccessible(true);
        List<?> defaultSerializers = (List<?>) defaultSerializersField.get(kryo);
        assertEquals("The registered default serializers changed.\n" +
                "Because serialization compatibility shall be checked" +
                " for default serializers, you must extend SerializationCompatTestData.TestData to have a field for the" +
                " type of the new default serializer.\n" +
                "After that's done, you must create new versions of 'test/resources/data*'" +
                " because the new TestData instance will no longer be equals the formerly written/serialized one.",
                33, defaultSerializers.size());
    }

    public void testStandard () throws Exception {
        runTest(
                "standard",
                new Function1<File, Input>() {
                    public Input apply(File file) throws FileNotFoundException {
                        return new Input(new FileInputStream(file));
                    }
                },
                new Function1<File, Output>() {
                    public Output apply(File file) throws Exception {
                        return new Output(new FileOutputStream(file));
                    }
                }
        );
    }

    public void testByteBuffer () throws Exception {
        runTest(
                "bytebuffer",
                new Function1<File, Input>() {
                    public Input apply(File file) throws FileNotFoundException {
                        return new ByteBufferInput(new FileInputStream(file));
                    }
                },
                new Function1<File, Output>() {
                    public Output apply(File file) throws Exception {
                        return new ByteBufferOutput(new FileOutputStream(file));
                    }
                }
        );
    }

    public void testFast () throws Exception {
        runTest(
                "fast",
                new Function1<File, Input>() {
                    public Input apply(File file) throws FileNotFoundException {
                        return new FastInput(new FileInputStream(file));
                    }
                },
                new Function1<File, Output>() {
                    public Output apply(File file) throws Exception {
                        return new FastOutput(new FileOutputStream(file));
                    }
                }
        );
    }

    public void testUnsafe () throws Exception {
        runTest(
                "unsafe-" + ENDIANNESS,
                new Function1<File, Input>() {
                    public Input apply(File file) throws FileNotFoundException {
                        return new UnsafeInput(new FileInputStream(file));
                    }
                },
                new Function1<File, Output>() {
                    public Output apply(File file) throws Exception {
                        return new UnsafeOutput(new FileOutputStream(file));
                    }
                }
        );
    }

    public void testUnsafeMemory () throws Exception {
        runTest(
                "unsafeMemory-" + ENDIANNESS,
                new Function1<File, Input>() {
                    public Input apply(File file) throws FileNotFoundException {
                        return new UnsafeMemoryInput(new FileInputStream(file));
                    }
                },
                new Function1<File, Output>() {
                    public Output apply(File file) throws Exception {
                        return new UnsafeMemoryOutput(new FileOutputStream(file));
                    }
                }
        );
    }

    private void runTest(String variant, Function1<File, Input> inputFactory, Function1<File, Output> outputFactory) throws Exception {
        File file = new File("test/resources/data-"+ variant +".ser");
        file.getParentFile().mkdirs();

        if(file.exists()) {
            Log.info("Reading and testing data with mode '"+ variant +"' from file " + file.getAbsolutePath());
            Input in = inputFactory.apply(file);
            readAndRunTest(in);
            in.close();
        }
        else {
            Log.info("Testing and writing data with mode '"+ variant +"' to file " + file.getAbsolutePath());
            Output out = outputFactory.apply(file);
            runTestAndWrite(out);
            out.close();
        }
    }

    private void readAndRunTest (Input in) throws FileNotFoundException {
        TestData actual = kryo.readObject(in, TestData.class);
        roundTrip(1646, 1754, actual);
        try {
            assertReflectionEquals(actual, new TestData());
        } catch(AssertionError e) {
            Log.info("Serialization format is broken, please check " + getClass().getSimpleName() + "'s class doc to see" +
                    " what this means and how to proceed.");
            throw e;
        }
    }

    private void runTestAndWrite (Output out) throws FileNotFoundException {
        TestData testData = new TestData();
        roundTrip(1646, 1754, testData);
        kryo.writeObject(out, testData);
    }

    @Override
    protected void doAssertEquals( final Object one, final Object another ) {
        try {
            assertReflectionEquals(one, another);
        } catch (Exception e) {
            fail("Test failed: " + e);
        }
    }

    private interface Function1<A, B> {
        B apply(A input) throws Exception;
    }
	
}