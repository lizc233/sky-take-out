package com.sky.service;


import com.sky.dto.*;
import com.sky.entity.OrderDetail;
import com.sky.entity.Orders;
import com.sky.result.PageResult;
import com.sky.vo.OrderStatisticsVO;
import com.sky.vo.OrderSubmitVO;
import com.sky.vo.OrderVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public interface OrderService {

    /**
     * 用户下单
     * @param ordersSubmitDTO
     * @return
     */
    OrderSubmitVO submitOrder(OrdersSubmitDTO ordersSubmitDTO);

    PageResult pageQuery(int page, int pageSize, Integer status);

    void paySuccess(String outTradeNo);

    OrderVO getOrderDetail(Long id);

    void cancel(Long id);

    void repetition(Long id);

    PageResult conditionSearch(OrdersPageQueryDTO ordersPageQueryDTO);

    OrderStatisticsVO statistics();

    void confirm(OrdersConfirmDTO ordersConfirmDTO);

    void rejection(OrdersRejectionDTO ordersRejectionDTO)throws Exception;

    void cancelAdmin(OrdersCancelDTO ordersCancelDTO)throws Exception;

    void delivery(Long id);

    void complete(Long id);

    void reminder(Long id);
}
