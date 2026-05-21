package com.example.libraryseat.borrow.dto;

public record WarnOverdueResult(String message, int warningCount, boolean accountFrozen) {}
