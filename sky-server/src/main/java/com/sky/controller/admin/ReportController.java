package com.sky.controller.admin;


import com.sky.result.Result;
import com.sky.service.ReportService;
import com.sky.vo.*;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletResponse;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Locale;

/*
* 数据统计相关接口
*
* */
@RestController
@Slf4j
@Api(tags="数据统计接口")
@RequestMapping("/admin/report")
public class ReportController {
    @Autowired
    private ReportService reportService;

    /**
     * 营业额统计
     * @param begin
     * @param end
     * @return
     */
    @GetMapping("/turnoverStatistics")
    @ApiOperation("营业额统计")
    public Result<TurnoverReportVO> turnoverStatistics(
            @DateTimeFormat(pattern="yyyy-MM-dd") LocalDate begin,
            @DateTimeFormat(pattern="yyyy-MM-dd") LocalDate end){
        log.info("营业额统计：{}到{}",begin,end);
        return Result.success(reportService.getTurnoverStatistics(begin,end));

    }

    @ApiOperation("用户统计")
    @GetMapping("/userStatistics")
    public Result<UserReportVO> userStatistics(
            @DateTimeFormat(pattern="yyyy-MM-dd") LocalDate begin,
            @DateTimeFormat(pattern="yyyy-MM-dd") LocalDate end){
        log.info("用户统计：{}到{}",begin,end);
        return Result.success(reportService.getUserStatistics(begin,end));
    }

    @ApiOperation("订单统计")
    @GetMapping("/orderStatistics")
    public Result<OrderReportVO> orderStatistics(
            @DateTimeFormat(pattern="yyyy-MM-dd") LocalDate begin,
            @DateTimeFormat(pattern="yyyy-MM-dd") LocalDate end){
        log.info("订单统计：{}到{}",begin,end);
        return Result.success(reportService.getOrderStatistics(begin,end));
    }
    @ApiOperation("top10排名")
    @GetMapping("/top10")
    public Result<SalesTop10ReportVO> top(
            @DateTimeFormat(pattern="yyyy-MM-dd") LocalDate begin,
            @DateTimeFormat(pattern="yyyy-MM-dd") LocalDate end){
        log.info("销量top10：{}到{}",begin,end);
        return Result.success(reportService.getSalesTop10Statistics(begin,end));
    }

    /**
     * 数据导出
     * @param response
     */
    @GetMapping("/export")
    @ApiOperation("导出数据")
    public void export(HttpServletResponse response){
        reportService.exportBusinessData(response);
    }


}
