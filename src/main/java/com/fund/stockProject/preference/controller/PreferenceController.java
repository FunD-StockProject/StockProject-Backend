package com.fund.stockProject.preference.controller;

import com.fund.stockProject.preference.dto.BookmarkInfoResponse;
import com.fund.stockProject.preference.dto.StockPreferenceResponse;
import com.fund.stockProject.preference.service.PreferenceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.Parameter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "선호도 (Preference)", description = "사용자 종목 선호(북마크/숨김/알림 설정) 관리 API")
@RestController
@RequestMapping("/preference")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class PreferenceController {

    private final PreferenceService preferenceService;

    @PostMapping("/bookmark/{stockId}")
    @Operation(summary = "북마크 추가", description = "주어진 종목 ID를 사용자 북마크 목록에 추가합니다. 이미 추가되어 있다면 비즈니스 로직에 따라 무시 또는 에러 처리.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "추가 성공"),
            @ApiResponse(responseCode = "404", description = "종목 미존재", content = @Content),
            @ApiResponse(responseCode = "500", description = "서버 오류", content = @Content)
    })
    public ResponseEntity<Map<String, String>> addBookmark(
            @Parameter(description = "북마크할 종목 ID", example = "123", required = true)
            @PathVariable Integer stockId) {
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
    @Operation(summary = "북마크 해제", description = "주어진 종목 ID를 사용자 북마크 목록에서 제거합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "해제 성공"),
            @ApiResponse(responseCode = "404", description = "종목 또는 북마크 미존재", content = @Content),
            @ApiResponse(responseCode = "500", description = "서버 오류", content = @Content)
    })
    public ResponseEntity<Map<String, String>> removeBookmark(
            @Parameter(description = "북마크 해제할 종목 ID", example = "123", required = true)
            @PathVariable Integer stockId) {
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
    @Operation(summary = "종목 숨김 처리", description = "지정한 종목을 '다시는 보지 않음' 목록에 추가합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "숨김 성공"),
            @ApiResponse(responseCode = "404", description = "종목 미존재", content = @Content),
            @ApiResponse(responseCode = "500", description = "서버 오류", content = @Content)
    })
    public ResponseEntity<Map<String, String>> hideStock(
            @Parameter(description = "숨김 처리할 종목 ID", example = "321", required = true)
            @PathVariable Integer stockId) {
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
    @Operation(summary = "숨김 해제", description = "'다시는 보지 않음' 처리했던 종목을 다시 목록에 보이게 합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "해제 성공"),
            @ApiResponse(responseCode = "404", description = "종목 또는 숨김 기록 미존재", content = @Content),
            @ApiResponse(responseCode = "500", description = "서버 오류", content = @Content)
    })
    public ResponseEntity<Map<String, String>> showStock(
            @Parameter(description = "숨김 해제할 종목 ID", example = "321", required = true)
            @PathVariable Integer stockId) {
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
    @Operation(summary = "북마크 목록", description = "사용자의 현재 북마크된 종목 리스트를 반환합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공", content = @Content(array = @ArraySchema(schema = @Schema(implementation = BookmarkInfoResponse.class))))
    })
    public ResponseEntity<List<BookmarkInfoResponse>> getBookmarkList() {
        return ResponseEntity.ok().body(preferenceService.getBookmarks());
    }

    @GetMapping("/bookmark/count")
    @Operation(summary = "북마크 개수", description = "사용자 북마크 총 개수를 반환합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공")
    })
    public ResponseEntity<Integer> getBookmarkCount() {
        return ResponseEntity.ok().body(preferenceService.getBookmarkCount());
    }

    @GetMapping("/notification/count")
    @Operation(summary = "변동알림 개수", description = "사용자의 북마크 중 알림이 활성화된 종목 개수를 반환합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공")
    })
    public ResponseEntity<Integer> getNotificationCount() {
        return ResponseEntity.ok().body(preferenceService.getNotificationCount());
    }

    @PatchMapping("/notification/toggle/{stockId}")
    @Operation(summary = "알림 토글", description = "해당 종목의 알림을 토글합니다. 북마크가 없으면 북마크+알림을 생성하고, 있으면 알림만 토글합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "토글 성공"),
            @ApiResponse(responseCode = "404", description = "종목 미존재", content = @Content),
            @ApiResponse(responseCode = "500", description = "서버 오류", content = @Content)
    })
    public ResponseEntity<Map<String, String>> toggleNotification(
            @Parameter(description = "알림 토글할 종목 ID", example = "555", required = true)
            @PathVariable Integer stockId) {
        try {
            boolean isNotificationEnabled = preferenceService.toggleNotification(stockId);
            String message = isNotificationEnabled ? "Notification enabled successfully" : "Notification disabled successfully";
            
            return ResponseEntity.status(HttpStatus.OK)
                    .body(Map.of("message", message, "notificationEnabled", String.valueOf(isNotificationEnabled)));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Failed to toggle notification: " + e.getMessage()));
        }
    }

    @GetMapping("/stock/{stockId}")
    @Operation(summary = "종목 관심/알림 여부 조회", description = "주어진 종목 ID에 대한 사용자의 관심 종목(북마크) 여부와 알림 활성화 여부를 반환합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공", content = @Content(schema = @Schema(implementation = StockPreferenceResponse.class))),
            @ApiResponse(responseCode = "401", description = "인증 실패", content = @Content)
    })
    public ResponseEntity<StockPreferenceResponse> getStockPreference(
            @Parameter(description = "조회할 종목 ID", example = "123", required = true)
            @PathVariable Integer stockId) {
        StockPreferenceResponse response = preferenceService.getStockPreference(stockId);
        return ResponseEntity.ok(response);
    }
}
