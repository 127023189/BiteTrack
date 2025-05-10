package com.sky.service.impl;

import com.sky.dto.GoodsSalesDTO;
import com.sky.entity.Orders;
import com.sky.mapper.OrderMapper;
import com.sky.mapper.UserMapper;
import com.sky.service.ReportService;
import com.sky.service.WorkspaceService;
import com.sky.vo.*;
import org.apache.commons.lang.StringUtils;
import org.apache.poi.util.StringUtil;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

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
public class ReportServiceImpI implements ReportService {

    @Autowired
    private OrderMapper ordersMapper;
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
    @Override
    public TurnoverReportVO getTurnover(LocalDate begin, LocalDate end) {
        List<LocalDate> dateList = new ArrayList<>();

        dateList.add(begin);
        // 开始日期加1，直到结束日期
        while (!begin.equals(end)){
            begin = begin.plusDays(1);
            dateList.add(begin);
        }

        List<Double> turnoverList = new ArrayList<>();
        for(LocalDate date : dateList){
            // 获取营业额数据
            LocalDateTime beginTime = LocalDateTime.of(date, LocalTime.MIN);
            LocalDateTime endTime = LocalDateTime.of(date, LocalTime.MAX);

            Map map = new HashMap();
            map.put("begin", beginTime);
            map.put("end", endTime);
            // 已经完成的项目
            map.put("status", Orders.COMPLETED);
            Double turnover = ordersMapper.sumByMap(map);
            turnover = turnover == null ? 0.0 : turnover;
            turnoverList.add(turnover);
        }

        return TurnoverReportVO.builder()
                .dateList(StringUtils.join(dateList, ","))
                .turnoverList(StringUtils.join(turnoverList, ","))
                .build();
    }

    /**
     * 用户统计
     * @param begin
     * @param end
     * @return
     */
    @Override
    public UserReportVO getUserStatistics(LocalDate begin, LocalDate end) {
       List<LocalDate> dateList = new ArrayList<>();
       dateList.add(begin);
       while (!begin.equals(end)){
           begin = begin.plusDays(1);
           dateList.add(begin);
       }

       List<Integer> newUserList = new ArrayList<>();
       List<Integer> totalUserList = new ArrayList<>();

       for(LocalDate date : dateList){
           LocalDateTime beginTime = LocalDateTime.of(date, LocalTime.MIN);
           LocalDateTime endTime = LocalDateTime.of(date, LocalTime.MAX);

           Integer newUser = getUserCount(beginTime, endTime);
           Integer totalUser = getUserCount(null, endTime);

           newUserList.add(newUser);
           totalUserList.add(totalUser);
       }
       return UserReportVO.builder()
               .dateList(StringUtils.join(dateList, ","))
               .newUserList(StringUtils.join(newUserList, ","))
               .totalUserList(StringUtils.join(totalUserList, ","))
               .build();
    }

    /**
     * 订单统计
     * @param begin
     * @param end
     * @return
     */
    @Override
    public OrderReportVO getOrderStatistics(LocalDate begin, LocalDate end) {
        List<LocalDate> dateList = new ArrayList<>();
        dateList.add(begin);

        while (!begin.equals(end)){
            begin = begin.plusDays(1);
            dateList.add(begin);
        }

        // 每天的订单总数
        List<Integer> orderCountList = new ArrayList<>();
        // 每天有效的订单总数
        List<Integer> validOrderCountList = new ArrayList<>();
        for(LocalDate date : dateList){
            LocalDateTime beginTime = LocalDateTime.of(date, LocalTime.MIN);
            LocalDateTime endTime = LocalDateTime.of(date, LocalTime.MAX);

            // 获取订单总数
            Integer orderCount = getOrderCount(beginTime, endTime, null);

            // 获取有效订单总数
            Integer validOrderCount = getOrderCount(beginTime, endTime, Orders.COMPLETED);

            orderCountList.add(orderCount);
            validOrderCountList.add(validOrderCount);
        }

        // 获取总订单数
        Integer totalOrderCount = orderCountList.stream().reduce(Integer::sum).get();
        Integer validOrderCount = validOrderCountList.stream().reduce(Integer::sum).get();

        // 订单完成率
        Double orderCompletionRate = 0.0;
        if(totalOrderCount != 0){
            orderCompletionRate = validOrderCount.doubleValue() / totalOrderCount;
        }

        return OrderReportVO.builder()
                .dateList(StringUtils.join(dateList, ","))
                .orderCountList(StringUtils.join(orderCountList, ","))
                .validOrderCountList(StringUtils.join(validOrderCountList, ","))
                .totalOrderCount(totalOrderCount)
                .validOrderCount(validOrderCount)
                .orderCompletionRate(orderCompletionRate)
                .build();
    }

    /**
     * 销量排名
     * @param begin
     * @param end
     * @return
     */
    @Override
    public SalesTop10ReportVO getTop10(LocalDate begin, LocalDate end) {
        LocalDateTime beginTime = LocalDateTime.of(begin, LocalTime.MIN);
        LocalDateTime endTime = LocalDateTime.of(end, LocalTime.MAX);

        // 查询销量排名
        List<GoodsSalesDTO> salesTop10 = ordersMapper.getSalesTop10(beginTime, endTime);

        String nameList = StringUtils.join(salesTop10.stream().map(GoodsSalesDTO::getName).collect(Collectors.toList()), ",");

        String numberList = StringUtils.join(salesTop10.stream().map(x -> x.getNumber().toString()).collect(Collectors.toList()), ",");

        return SalesTop10ReportVO.builder()
                .nameList(nameList)
                .numberList(numberList)
                .build();
    }

    /**
     * 导出运营数据
     * @param response
     */
    @Override
    public void exportBusinessData(HttpServletResponse response) {
        LocalDate begin = LocalDate.now().minusDays(30);
        LocalDate end = LocalDate.now().minusDays(1);

        // 查询概览运营数据
        BusinessDataVO businessDataVO = workspaceService.getBusinessData(LocalDateTime.of(begin, LocalTime.MIN),LocalDateTime.of(end, LocalTime.MAX));

        InputStream inputStream = this.getClass().getResourceAsStream("/template/运营数据报表模板.xlsx");

        try {
            // 创建Excel对象
            XSSFWorkbook excel = new XSSFWorkbook(inputStream);
            // 获得excel文件中的一个sheet页
            XSSFSheet sheet = excel.getSheet("Sheet1");

            // 设置时间
            sheet.getRow(1).getCell(1).setCellValue(begin + "至" + end);

            XSSFRow row = sheet.getRow(3);

            row.getCell(2).setCellValue(businessDataVO.getTurnover());
            row.getCell(4).setCellValue(businessDataVO.getOrderCompletionRate());
            row.getCell(6).setCellValue(businessDataVO.getNewUsers());

            row = sheet.getRow(4);
            row.getCell(2).setCellValue(businessDataVO.getValidOrderCount());
            row.getCell(4).setCellValue(businessDataVO.getUnitPrice());

            for(int i = 0; i < 30; i++){
                LocalDate date = begin.plusDays(i);
                businessDataVO = workspaceService.getBusinessData(
                        LocalDateTime.of(date, LocalTime.MIN), LocalDateTime.of(date, LocalTime.MAX));

                row = sheet.getRow(7 + i);
                row.getCell(1).setCellValue(date.toString());
                row.getCell(2).setCellValue(businessDataVO.getTurnover());
                row.getCell(3).setCellValue(businessDataVO.getValidOrderCount());
                row.getCell(4).setCellValue(businessDataVO.getOrderCompletionRate());
                row.getCell(5).setCellValue(businessDataVO.getUnitPrice());
                row.getCell(6).setCellValue(businessDataVO.getNewUsers());
            }

            // 将输出流返回至客户端
            ServletOutputStream outputStream = response.getOutputStream();
            excel.write(outputStream);

            outputStream.flush();
            outputStream.close();
            excel.close();

        } catch (IOException e) {
            e.printStackTrace();
        }


    }

    /**
     * 获取订单数量
     * @param beginTime
     * @param endTime
     * @param status
     * @return
     */
    private Integer getOrderCount(LocalDateTime beginTime, LocalDateTime endTime, Integer status) {
        Map map = new HashMap();
        map.put("begin", beginTime);
        map.put("end", endTime);
        map.put("status", status);
        return ordersMapper.countByMap(map);
    }

    /**
     * 获取用户数量
     * @param begin
     * @param end
     * @return
     */
    private Integer getUserCount(LocalDateTime begin, LocalDateTime end){
        Map map = new HashMap();
        map.put("begin", begin);
        map.put("end", end);
        return userMapper.countByMap(map);
    }
}
