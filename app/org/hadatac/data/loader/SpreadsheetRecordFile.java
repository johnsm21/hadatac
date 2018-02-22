package org.hadatac.data.loader;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.apache.poi.EncryptedDocumentException;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

import com.avaje.ebeaninternal.server.lib.util.Str;

public class SpreadsheetRecordFile implements RecordFile {
	
	private File file;
	private String sheetName;
	
	public SpreadsheetRecordFile(File file, String sheetName) {
		this.file = file;
		this.sheetName = sheetName;
	}
	
	public SpreadsheetRecordFile(File file) {
		this.file = file;
		this.sheetName = "";
	}
	
	@Override
	public Iterable<Record> getRecords() {		
		try {
			Workbook workbook = WorkbookFactory.create(new FileInputStream(file));
			Sheet sheet = null;
			System.out.println("sheetName: " + sheetName);
			if (sheetName.isEmpty()) {
				sheet = workbook.getSheetAt(0);
			} else {
				sheet = workbook.getSheet(sheetName);
			}
			
			Iterator<Row> rows = sheet.iterator();
			
			Iterable<Row> iterable = () -> rows;
			Stream<Row> stream = StreamSupport.stream(iterable.spliterator(), false);
			
			return stream.skip(1)
					.filter(row -> !isEmptyRow(row))
					.map(row -> {
				return new SpreadsheetFileRecord(row);
			}).collect(Collectors.toList());
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (EncryptedDocumentException e) {
			e.printStackTrace();
		} catch (InvalidFormatException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		return null;
	}
	
	@Override
	public List<String> getHeaders() {
		try {
			Workbook workbook = WorkbookFactory.create(new FileInputStream(file));
			Sheet sheet = null;
			if (sheetName.isEmpty()) {
				sheet = workbook.getSheetAt(0);
			} else {
				sheet = workbook.getSheet(sheetName);
			}
			
			Iterator<Row> rows = sheet.iterator();
			while (rows.hasNext()) {
				Row header = rows.next();
				if (!isEmptyRow(header)) {
					return getRowValues(header);
				}
			}
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (EncryptedDocumentException e) {
			e.printStackTrace();
		} catch (InvalidFormatException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		return null;
	}

	@Override
	public File getFile() {
		return file;
	}
	
	private boolean isEmptyRow(Row row) {
		boolean isEmpty = true;
		for (int i = row.getFirstCellNum(); i <= row.getLastCellNum(); i++) {
			if (row.getCell(i) != null && !row.getCell(i).toString().trim().isEmpty()) {
				isEmpty = false;
				break;
			}
		}
		
		return isEmpty;
	}
	
	private List<String> getRowValues(Row row) {
		List<String> values = new ArrayList<String>();
		for (int i = row.getFirstCellNum(); i <= row.getLastCellNum(); i++) {
			if (row.getCell(i) != null) {
				values.add(row.getCell(i).toString());
			} else {
				values.add("");
			}
		}
		
		return values;
	}
}
