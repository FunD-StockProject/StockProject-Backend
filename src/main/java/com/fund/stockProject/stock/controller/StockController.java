package com.fund.stockProject.stock.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/stock")
public class StockController {

//    @ApiOperation("뉴스 게시글 작성")
//    @PostMapping("/post")
//    public ResponseEntity<String> postArticle(@RequestPart(value = "data", required = false) PostArticleReq postArticleReq){
//        articleService.postArticle(postArticleReq);
//        return ResponseEntity.ok("post success");
//    }

    @GetMapping("/hello")
    public ResponseEntity<String> hello() {
        return ResponseEntity.ok("Hello, Swagger!");
    }
}
