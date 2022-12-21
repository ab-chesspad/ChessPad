/*
     Copyright (C) 2021-2022	Alexander Bootman, alexbootman@gmail.com

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.

 * test DocFilAx operations
 * Created by Alexander Bootman on 9/26/2022.
 */
package com.ab.pgn.io;

import com.ab.pgn.BaseTest;

import org.junit.Test;

import java.io.File;

public class FilAxImpTest extends BaseTest {

    @Test
    public void testRename() throws Exception {
        currentRootPath = TEST_TMP_ROOT;

        final String[][] fileItems = {
                {"x/dir/y", "z"},
                {"x/dir/books1.zip", "b2.zip"},
        };

        for (String[] fileItem : fileItems) {
            File testFile = toTempTest(fileItem[0]);
        }
    }

}
