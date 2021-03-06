/*
 * Copyright 2012-2015 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.boot.loader.jar;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.loader.data.RandomAccessData;
import org.springframework.boot.loader.data.RandomAccessData.ResourceAccess;

/**
 * Parses the central directory from a JAR file.
 *
 * @author Phillip Webb
 * @see CentralDirectoryVistor
 */
class CentralDirectoryParser {

	private int CENTRAL_DIRECTORY_HEADER_BASE_SIZE = 46;

	private final List<CentralDirectoryVistor> vistors = new ArrayList<CentralDirectoryVistor>();

	public <T extends CentralDirectoryVistor> T addVistor(T vistor) {
		this.vistors.add(vistor);
		return vistor;
	}

	/**
	 * Parse the source data, triggering {@link CentralDirectoryVistor vistors}.
	 * @param data the source data
	 * @param skipPrefixBytes if prefix bytes should be skipped
	 * @return The actual archive data without any prefix bytes
	 * @throws IOException on error
	 */
	public RandomAccessData parse(RandomAccessData data, boolean skipPrefixBytes)
			throws IOException {
		CentralDirectoryEndRecord endRecord = new CentralDirectoryEndRecord(data);
		if (skipPrefixBytes) {
			data = getArchiveData(endRecord, data);
		}
		RandomAccessData centralDirectoryData = endRecord.getCentralDirectory(data);
		visitStart(endRecord, centralDirectoryData);
		InputStream inputStream = centralDirectoryData
				.getInputStream(ResourceAccess.ONCE);
		try {
			int dataOffset = 0;
			for (int i = 0; i < endRecord.getNumberOfRecords(); i++) {
				CentralDirectoryFileHeader fileHeader = CentralDirectoryFileHeader
						.fromInputStream(inputStream);
				visitFileHeader(dataOffset, fileHeader);
				dataOffset += this.CENTRAL_DIRECTORY_HEADER_BASE_SIZE
						+ fileHeader.getName().length() + fileHeader.getComment().length()
						+ fileHeader.getExtra().length;
			}
		}
		finally {
			inputStream.close();
		}
		visitEnd();
		return data;
	}

	private RandomAccessData getArchiveData(CentralDirectoryEndRecord endRecord,
			RandomAccessData data) {
		long offset = endRecord.getStartOfArchive(data);
		if (offset == 0) {
			return data;
		}
		return data.getSubsection(offset, data.getSize() - offset);
	}

	private void visitStart(CentralDirectoryEndRecord endRecord,
			RandomAccessData centralDirectoryData) {
		for (CentralDirectoryVistor vistor : this.vistors) {
			vistor.visitStart(endRecord, centralDirectoryData);
		}
	}

	private void visitFileHeader(int dataOffset, CentralDirectoryFileHeader fileHeader) {
		for (CentralDirectoryVistor vistor : this.vistors) {
			vistor.visitFileHeader(fileHeader, dataOffset);
		}
	}

	private void visitEnd() {
		for (CentralDirectoryVistor vistor : this.vistors) {
			vistor.visitEnd();
		}
	}

}
