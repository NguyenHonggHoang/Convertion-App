package com.example.converter.dto.exchange;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class FxHistoryResponse {
    private String base;
    private String quote;
    private List<FxPointDTO> history;
}