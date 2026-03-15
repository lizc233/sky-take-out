package com.sky.service.impl;

//import com.aliyun.oss.common.utils.StringUtils;
import com.sky.dto.GoodsSalesDTO;
import com.sky.entity.Orders;
import com.sky.mapper.OrderMapper;
import com.sky.mapper.UserMapper;
import com.sky.service.ReportService;
import com.sky.service.WorkspaceService;
import com.sky.vo.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.apache.commons.lang3.StringUtils;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ReportServiceImpl implements ReportService {

    @Autowired
    private OrderMapper orderMapper;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private WorkspaceService workspaceService;

    /**
     * 营业额统计
     * @param begin
     * @param end
     * @return
     */
    public TurnoverReportVO getTurnoverStatistics(LocalDate begin, LocalDate end){
        List<LocalDate> dateList = new ArrayList<>();
        dateList.add(begin);
        while(begin.equals((end))){
            // 日期加计算，计算指定日期的后一天
            begin = begin.plusDays(1);
            dateList.add(begin);
        }
        //存放每天的营业额
        List<Double> turnoverList = new ArrayList<>();
        for(LocalDate date : dateList){
            //查询date日期对应的营业额数据，营业额是指:状态为“已完成”的订单金额合计
            LocalDateTime beginTime=LocalDateTime.of(date, LocalTime.MIN);
            LocalDateTime endTime=LocalDateTime.of(date,LocalTime.MAX);
            Map map = new HashMap();
            map.put("begin",beginTime);
            map.put("end",endTime);
            map.put("status", Orders.COMPLETED);
            Double turnover = orderMapper.sumByMap(map);
            turnover= turnover==null?0.0:turnover;
            turnoverList.add(turnover);
        }
        //将集合的每个元素取出来，创建一个字符串，用,来分割
        // 现在这样写是完全合法的，它会调用 LocalDate 的 toString()
        return TurnoverReportVO.builder()
                .dateList(StringUtils.join(dateList, ","))
                .turnoverList(StringUtils.join(turnoverList, ","))
                .build();
    }

    /*
    *用户统计
    *@ return
    *
    * */
    public UserReportVO getUserStatistics(LocalDate begin, LocalDate end) {
        List<LocalDate> dateList = new ArrayList<>();
        dateList.add(begin);
        while(begin.equals((end))){
            // 日期加计算，计算指定日期的后一天
            begin = begin.plusDays(1);
            dateList.add(begin);
        }
        //存放每天的新增用户数据
        List<Integer> newUserList = new ArrayList<>();
        //存放每天的总用户数量
        List<Integer> totalUserList = new ArrayList<>();
        for(LocalDate date: dateList){
            LocalDateTime beginTime=LocalDateTime.of(date, LocalTime.MIN);
            LocalDateTime endTime=LocalDateTime.of(date,LocalTime.MAX);
            Map map = new HashMap();
            map.put("end",endTime);
            totalUserList.add(userMapper.countByMap(map));
            map.put("begin",beginTime);
            newUserList.add(userMapper.countByMap(map));
        }
        return UserReportVO.builder()
                .dateList(StringUtils.join(dateList, ","))
                .totalUserList(StringUtils.join(totalUserList, ","))
                .newUserList(StringUtils.join(newUserList, ","))
                .build();
    }

    /*
    * 订单统计
    *
    * */
    public OrderReportVO getOrderStatistics(LocalDate begin, LocalDate end) {
        List<LocalDate> dateList = new ArrayList<>();
        dateList.add(begin);
        while(begin.equals((end))){
            // 日期加计算，计算指定日期的后一天
            begin = begin.plusDays(1);
            dateList.add(begin);
        }
        List<Integer> orderCountList = new ArrayList<>();
        List<Integer> validOrderCountList = new ArrayList<>();


        for(LocalDate date: dateList){
            LocalDateTime beginTime=LocalDateTime.of(date, LocalTime.MIN);
            LocalDateTime endTime=LocalDateTime.of(date,LocalTime.MAX);
            /*Map map = new HashMap();
            map.put("begin",beginTime);
            map.put("end",endTime);
            orderCount. orderMapper.sumByDateMap(map)
            orderCountList.add(orderCount);
            map.put("status", Orders.COMPLETED);*/
/*
            validOrderCount = orderMapper.sumByDateMap(map)
            validOrderCountList.add(validOrderCount);*/
            Integer orderCount = countOrder(begin, end, null);
            Integer validOrderCount = countOrder(begin, end, Orders.COMPLETED);
            orderCountList.add(orderCount);
            validOrderCountList.add(validOrderCount);
        }
        //计算时间内的订单数
        Integer orderCount = orderCountList.stream().reduce(Integer::sum).get();
        //计算时间内的有效订单数
        Integer validOrderCount=validOrderCountList.stream().reduce(Integer::sum).get();
        Double orderCompletionRate = 0.0;
        if (orderCount!=0){
            orderCompletionRate= validOrderCount.doubleValue()/orderCount;
        }
        return OrderReportVO.builder()
                .validOrderCountList(StringUtils.join(validOrderCountList, ","))
                .orderCountList(StringUtils.join(orderCountList, ","))
                .dateList(StringUtils.join(dateList, ","))
                .orderCompletionRate(orderCompletionRate)
                .totalOrderCount(orderCount)
                .validOrderCount(validOrderCount)
                .build();
    }

    /*
    * 销量top10排名
    *
    * */
    public SalesTop10ReportVO getSalesTop10Statistics(LocalDate begin, LocalDate end) {
        LocalDateTime beginTime = LocalDateTime.of(begin, LocalTime.MIN);
        LocalDateTime endTime = LocalDateTime.of(end, LocalTime.MAX);
        List<GoodsSalesDTO> salesTop = orderMapper.getSalesTop(beginTime, endTime);
        List<String> collectName = salesTop.stream().map(GoodsSalesDTO::getName).collect(Collectors.toList());
        List<Integer> collectNumber = salesTop.stream().map(GoodsSalesDTO::getNumber).collect(Collectors.toList());
        return SalesTop10ReportVO
                .builder()
                .nameList(StringUtils.join(collectName, ","))
                .numberList(StringUtils.join(collectNumber, ","))
                .build();
    }

    /*
    * 导出运营数据
    *
    * */
    public void exportBusinessData(HttpServletResponse response) {

        //查询数据库，获取营业数据
        LocalDate dateBegin = LocalDate.now().minusDays(30);
        LocalDate dateEnd = LocalDate.now().minusDays(1);

        BusinessDataVO businessData = workspaceService.getBusinessData(LocalDateTime.of(dateBegin, LocalTime.MIN), LocalDateTime.of(dateEnd, LocalTime.MAX));


        //通过POI将数据写入excel文件
        InputStream in = this.getClass().getClassLoader().getResourceAsStream("template/运营数据报表模板.xlsx");
        //基于模板文件创建一个新的excel文件
        try {
            XSSFWorkbook excel=new XSSFWorkbook(in);

            //填充数据--时间
            XSSFSheet sheet1 = excel.getSheet("Sheet1");
            sheet1.getRow(1).getCell(1).setCellValue("时间:"+ dateBegin+"至"+dateEnd);

            //获得第4行
            XSSFRow row=sheet1.getRow(3);
            row.getCell(2).setCellValue(businessData.getTurnover());
            row.getCell(4).setCellValue(businessData.getOrderCompletionRate());
            row.getCell(6).setCellValue(businessData.getNewUsers());

            //获得第5行
            XSSFRow row5 =sheet1.getRow(4);
            row.getCell(2).setCellValue(businessData.getValidOrderCount());
            row.getCell(4).setCellValue(businessData.getUnitPrice());

            //填充明细数据
            for(int i=0;i<30;i++){
                LocalDate date =dateBegin.plusDays(i);
                //查询某一天的营业数据
                BusinessDataVO businessData1 = workspaceService.getBusinessData(LocalDateTime.of(date, LocalTime.MIN), LocalDateTime.of(date, LocalTime.MAX));

                //获得某一行
                row=sheet1.getRow(7+i);
                row.getCell(1).setCellValue(date.toString());
                row.getCell(2).setCellValue(businessData1.getTurnover());
                row.getCell(3).setCellValue(businessData1.getValidOrderCount());
                row.getCell(4).setCellValue(businessData1.getOrderCompletionRate());
                row.getCell(5).setCellValue(businessData1.getUnitPrice());
                row.getCell(6).setCellValue(businessData1.getNewUsers());

            }

            //通过输出流将文件下载到浏览器
            ServletOutputStream out =response.getOutputStream();
            //关闭资源
            out.close();
            excel.close();
            excel.write(out);
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    /*
    * 根据条件统计订单数量
    *
    * */
    private Integer countOrder(LocalDate begin, LocalDate end, Integer status) {
/*        LocalDateTime beginTime = LocalDateTime.of(begin, LocalTime.MIN);
        LocalDateTime endTime = LocalDateTime.of(end, LocalTime.MAX);*/
        Map map = new HashMap();
        map.put("begin", begin);
        map.put("end", end);
        map.put("status", status);
        return orderMapper.sumByDateMap(map);
    }


}
