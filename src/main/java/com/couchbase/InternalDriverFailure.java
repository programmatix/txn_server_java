/*
 * Copyright (c) 2020 Couchbase, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.couchbase;

// Throw this whenever hitting some internal failure of this driver, e.g. an unsupported
// command or hook.  It will bypass all the transaction error handling and fail the test
// with a nice clear error.
public class InternalDriverFailure extends RuntimeException {
    public InternalDriverFailure(RuntimeException cause) {
        super(cause);
    }
}
