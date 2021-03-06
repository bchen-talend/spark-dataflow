/*
 * Copyright (c) 2015, Cloudera, Inc. All Rights Reserved.
 *
 * Cloudera, Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"). You may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * This software is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License for
 * the specific language governing permissions and limitations under the
 * License.
 */

package com.cloudera.dataflow.hadoop;

import java.io.InputStream;
import java.io.OutputStream;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.google.cloud.dataflow.sdk.coders.Coder;
import org.apache.hadoop.io.NullWritable;

public final class NullWritableCoder extends WritableCoder<NullWritable> {
  private static final long serialVersionUID = 1L;

  @JsonCreator
  public static NullWritableCoder of() {
    return INSTANCE;
  }

  private static final NullWritableCoder INSTANCE = new NullWritableCoder();

  private NullWritableCoder() {
    super(NullWritable.class);
  }

  @Override
  public void encode(NullWritable value, OutputStream outStream, Context context) {
    // nothing to write
  }

  @Override
  public NullWritable decode(InputStream inStream, Context context) {
    return NullWritable.get();
  }

  @Override
  public boolean consistentWithEquals() {
    return true;
  }

  /**
   * Returns true since registerByteSizeObserver() runs in constant time.
   */
  @Override
  public boolean isRegisterByteSizeObserverCheap(NullWritable value, Context context) {
    return true;
  }

  @Override
  protected long getEncodedElementByteSize(NullWritable value, Context context) {
    return 0;
  }

  @Override
  public void verifyDeterministic() throws Coder.NonDeterministicException {
    // NullWritableCoder is deterministic
  }
}
