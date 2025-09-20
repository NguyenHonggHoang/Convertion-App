package com.example.converter.service.exchange;

import com.example.converter.dto.exchange.FxHistoryResponse;
import com.example.converter.dto.exchange.FxPointDTO;
import com.example.converter.entity.ExchangeRateHistory;
import com.example.converter.repository.ExchangeRateHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ExchangeHistoryService {
    
    private final ExchangeRateHistoryRepository repo;

    @Cacheable(value="fxHistory", key="#base+'|'+#quote+'|'+#from+'|'+#to+'|'+#limit", unless="#result==null || #result.history.isEmpty()")
    public FxHistoryResponse getHistory(String base, String quote, LocalDate from, LocalDate to, Integer limit) {
        List<ExchangeRateHistory> rows = repo.findHistoryForPredict(base, quote, from, to);
        if (rows.isEmpty()) {
            return new FxHistoryResponse(base, quote, List.of());
        }

        if (limit != null && limit > 0 && rows.size() > limit) {
            rows = rows.subList(Math.max(0, rows.size() - limit), rows.size());
        }
        
        List<FxPointDTO> pts = rows.stream()
            .map(r -> new FxPointDTO(r.getRecordedAt().toLocalDate(), r.getRate()))
            .toList();
            
        return new FxHistoryResponse(base, quote, pts);
    }
}