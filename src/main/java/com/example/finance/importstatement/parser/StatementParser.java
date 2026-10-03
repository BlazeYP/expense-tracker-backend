package com.example.finance.importstatement.parser;

public interface StatementParser {

    boolean supports(String fileName, byte[] content);

    ParsedStatement parse(String fileName, byte[] content);
}
