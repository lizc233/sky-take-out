package com.sky.controller.user;


import com.sky.dto.OrdersPageQueryDTO;
import com.sky.dto.OrdersPaymentDTO;
import com.sky.dto.OrdersSubmitDTO;
import com.sky.entity.OrderDetail;
import com.sky.entity.Orders;
import com.sky.result.PageResult;
import com.sky.result.Result;
import com.sky.service.OrderService;
import com.sky.vo.OrderPaymentVO;
import com.sky.vo.OrderSubmitVO;
import com.sky.vo.OrderVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController("userOrderController")
@RequestMapping("user/order")
@Api(tags = "C端-订单接口")
@Slf4j
public class OrderController {

    @Autowired
    private OrderService orderService;

    /**
     * 用户下单
     * @param ordersSubmitDTO
     * @return
     */
    @ApiOperation("用户下单")
    @PostMapping("/submit")
    public Result<OrderSubmitVO> submit(@RequestBody OrdersSubmitDTO ordersSubmitDTO){
        log.info("用户下单：{}", ordersSubmitDTO);
        OrderSubmitVO orderSubmitVO = orderService.submitOrder(ordersSubmitDTO);
        return Result.success(orderSubmitVO);
    }

    @PutMapping("/payment")
    @ApiOperation("订单支付")
    public Result<OrderPaymentVO> payment(@RequestBody OrdersPaymentDTO ordersPaymentDTO) {
        log.info("订单支付：{}", ordersPaymentDTO);

        // 1. 【核心】直接在这里调用你写好的模拟支付成功逻辑
        // 这样数据库里的状态立刻就变成了“已支付/待接单”
        orderService.paySuccess(ordersPaymentDTO.getOrderNumber());

        // 2. 【关键】返回一个“空壳”VO，而不是 null
        // 这样前端执行 res.data.nonceStr 的时候，拿到的是 undefined 而不是报错
        OrderPaymentVO vo = new OrderPaymentVO();
        return Result.success(vo);
    }


    /**
     * 历史订单查询
     * @param page
     * @param pageSize
     * @param status
     * @return
     */
    @ApiOperation("历史订单查询")
    @GetMapping("/historyOrders")
    public Result<PageResult> page(int page, int pageSize, Integer status){
        log.info("历史订单查询：{},{},{}", page, pageSize, status);
        PageResult pageResult = orderService.pageQuery(page, pageSize, status);
        return Result.success(pageResult);
    }

    @GetMapping("/orderDetail/{id}")
    @ApiOperation("查询订单详情")
    @Transactional
    public Result<OrderVO> getOrderDetail(@PathVariable Long id ){
        log.info("查询订单详情：{}", id);
        OrderVO orderDetailVO = orderService.getOrderDetail(id);
        return Result.success(orderDetailVO);
    }

    /**
     * 取消订单
     * @param id
     * @return
     */
    @PutMapping("/cancel/{id}")
    @ApiOperation("取消订单")
    public Result cancel(@PathVariable Long id){
        log.info("取消订单：{}", id);
        orderService.cancel(id);
        return Result.success();
    }

    @PostMapping("/repetition/{id}")
    @ApiOperation("再来一单")
    public Result repetition(@PathVariable Long id){
        log.info("再来一单：{}", id);
        orderService.repetition(id);
        return Result.success();

    }

    /**
     * 订单搜索
     * @param id
     * @return
     */
    @GetMapping("/reminder/{id}")
    @ApiOperation("订单提醒")
    public Result reminder(@PathVariable("id") Long id){
        log.info("订单提醒：{}", id);
        orderService.reminder(id);
        return Result.success();
    }
}
