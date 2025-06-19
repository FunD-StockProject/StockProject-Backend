package com.fund.stockProject.preference.controller;

import com.fund.stockProject.preference.dto.BookmarkInfoResponse;
import com.fund.stockProject.preference.service.PreferenceService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/preference")
@RequiredArgsConstructor
public class PreferenceController {
    private final PreferenceService preferenceService;

    @PostMapping("/bookmark/{stockId}")
    @Operation(summary = "북마크 추가 API", description = "북마크 추가 API")
    public ResponseEntity<Map<String, String>> addBookmark(@PathVariable Integer stockId) {
        try {
            preferenceService.addBookmark(stockId);
            
            return ResponseEntity.status(HttpStatus.OK) // HTTP 200 OK
                    .body(Map.of("message", "Bookmark added successfully"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR) // HTTP 500 Internal Server Error
                    .body(Map.of("message", "Failed to add bookmark: " + e.getMessage()));
        }
    }

    @DeleteMapping("/bookmark/{stockId}")
    @Operation(summary = "북마크 해제 API", description = "북마크 해제 API")
    public ResponseEntity<Map<String, String>> removeBookmark(@PathVariable Integer stockId) {
        try {
            preferenceService.removeBookmark(stockId);
            
            return ResponseEntity.status(HttpStatus.OK) // HTTP 200 OK
                    .body(Map.of("message", "Bookmark removed successfully"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR) // HTTP 500 Internal Server Error
                    .body(Map.of("message", "Failed to remove bookmark: " + e.getMessage()));
        }
    }

    @PostMapping("/hide/{stockId}")
    @Operation(summary = "다신 보지 않음 API", description = "다신 보지 않음 API")
    public ResponseEntity<Map<String, String>> hideStock(@PathVariable Integer stockId) {
        try {
            preferenceService.hideStock(stockId);
            
            return ResponseEntity.status(HttpStatus.OK) // HTTP 200 OK
                    .body(Map.of("message", "Hide stock successfully"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR) // HTTP 500 Internal Server Error
                    .body(Map.of("message", "Failed to hide stock: " + e.getMessage()));
        }
    }

    @DeleteMapping("/hide/{stockId}")
    @Operation(summary = "다신 보지 않음 취소 API", description = "다신 보지 않음 취소 API")
    public ResponseEntity<Map<String, String>> showStock(@PathVariable Integer stockId) {
        try {
            preferenceService.showStock(stockId);

            return ResponseEntity.status(HttpStatus.OK) // HTTP 200 OK
                    .body(Map.of("message", "Show stock successfully"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR) // HTTP 500 Internal Server Error
                    .body(Map.of("message", "Failed to show stock: " + e.getMessage()));
        }
    }

    @GetMapping("/bookmark/list")
    @Operation(summary = "북마크 리스트 API", description = "북마크 리스트 API")
    public ResponseEntity<List<BookmarkInfoResponse>> getBookmarkList() {
        return ResponseEntity.ok().body(preferenceService.getBookmarks());
    }

    @GetMapping("/bookmark/count")
    @Operation(summary = "북마크 개수 API", description = "북마크 개수 API")
    public ResponseEntity<Integer> getBookmarkCount() {
        return ResponseEntity.ok().body(preferenceService.getBookmarkCount());
    }

}
