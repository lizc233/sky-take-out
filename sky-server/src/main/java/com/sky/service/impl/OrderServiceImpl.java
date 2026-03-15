package com.sky.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.sky.constant.MessageConstant;
import com.sky.context.BaseContext;
import com.sky.dto.*;
import com.sky.entity.AddressBook;
import com.sky.entity.OrderDetail;
import com.sky.entity.Orders;
import com.sky.entity.ShoppingCart;
import com.sky.exception.AddressBookBusinessException;
import com.sky.exception.OrderBusinessException;
import com.sky.exception.ShoppingCartBusinessException;
import com.sky.mapper.AddressBookMapper;
import com.sky.mapper.OrderDetailMapper;
import com.sky.mapper.OrderMapper;
import com.sky.mapper.ShoppingCartMapper;
import com.sky.result.PageResult;
import com.sky.result.Result;
import com.sky.service.OrderService;
import com.sky.utils.HttpClientUtil;
import com.sky.vo.OrderStatisticsVO;
import com.sky.vo.OrderSubmitVO;
import com.sky.vo.OrderVO;
//import lombok.Value;
import com.sky.websocket.WebSocketServer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PutMapping;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;

//import static jdk.javadoc.internal.doclets.formats.html.markup.HtmlStyle.details;

@Service
@Slf4j
public class OrderServiceImpl implements OrderService {
    @Autowired
    private OrderMapper orderMapper;
    @Autowired
    private OrderDetailMapper orderDetailMapper;
    @Autowired
    private AddressBookMapper addressBookMapper;
    @Autowired
    private ShoppingCartMapper shoppingCartMapper;

    @Value("${sky.baidu.address}")
    private String shopAddress;

    @Value("${sky.baidu.ak}")
    private String ak;

    @Autowired
    private WebSocketServer webSocketServer;;

    /*
    * 用户下单
    *
    * */
    @Transactional
    public OrderSubmitVO submitOrder(OrdersSubmitDTO ordersSubmitDTO) {


        //处理各种业务异常（地址簿为空、购物车数据为空）
        AddressBook addressBook = addressBookMapper.getById(ordersSubmitDTO.getAddressBookId());
        if(addressBook==null){
            throw new AddressBookBusinessException(MessageConstant.ADDRESS_BOOK_IS_NULL);
        }
        Long userId= BaseContext.getCurrentId();
        ShoppingCart shoppingCart=new ShoppingCart();
        shoppingCart.setUserId(userId);
        List<ShoppingCart> list = shoppingCartMapper.list(shoppingCart);
        if(list==null||list.size()==0){
            throw new ShoppingCartBusinessException(MessageConstant.SHOPPING_CART_IS_NULL);
        }

        //向订单表插入1条数据
        Orders orders=new Orders();
        BeanUtils.copyProperties(ordersSubmitDTO,orders);
        orders.setOrderTime(LocalDateTime.now());
        orders.setPayStatus(Orders.UN_PAID);
        orders.setStatus(Orders.PENDING_PAYMENT);
        orders.setNumber(String.valueOf(System.currentTimeMillis()));
        orders.setPhone(addressBook.getPhone());
        orders.setConsignee(addressBook.getConsignee());
        orders.setUserId(userId);
        orders.setAddress(addressBook.getDetail());


        orderMapper.insert(orders);

        List<OrderDetail> orderDetails=new ArrayList<>();
        //向订单明细表插入n条数据
        for(ShoppingCart cart : list){
            OrderDetail orderDetail=new OrderDetail();//订单明细
            BeanUtils.copyProperties(cart,orderDetail);
            orderDetail.setOrderId(orders.getId());//设置当前订单明细关联的订单id
            orderDetails.add(orderDetail);
        }


        // 1. 获取地址簿对象
        AddressBook addressBook1 = addressBookMapper.getById(orders.getAddressBookId());

        // 2. 🌟 关键：必须拼接成【完整地址】
        // 不要只传 addressBook.getDetail()，要传全称！
                String fullAddress = addressBook.getProvinceName() +
                        addressBook.getCityName() +
                        addressBook.getDistrictName() +
                        addressBook.getDetail();

        // 此时 fullAddress 应该是：北京市东城区金鱼胡同
                log.info("拼接后的完整收货地址：{}", fullAddress);

        // 3. 将【完整地址】传进去校验
                checkOutOfRange(fullAddress);
        orderDetailMapper.insertBatch(orderDetails);

        //下单成功后清空购物车数据
        shoppingCartMapper.deleteById(userId);

        //封装VO返回结果
        OrderSubmitVO orderSubmitVO= OrderSubmitVO.builder()
                .id(orders.getId())
                .orderNumber(orders.getNumber())
                .orderAmount(orders.getAmount())
                .orderTime(orders.getOrderTime())
                .build();
        return orderSubmitVO;
    }

    /*
    * 历史订单查询
    *
    * */
    public PageResult pageQuery(int pageNum, int pageSize, Integer status) {
        OrdersPageQueryDTO ordersPageQueryDTO=new OrdersPageQueryDTO();
        ordersPageQueryDTO.setUserId(BaseContext.getCurrentId());
        ordersPageQueryDTO.setStatus(status);
        PageHelper.startPage(pageNum, pageSize);
        Page<Orders> page = orderMapper.pageQuery(ordersPageQueryDTO);
        List<OrderVO> list = new ArrayList<>();
        //查出订单明细，并封装入OrderVO进行响应
        if(page!=null&&page.getTotal()>0){
            for(Orders orders : page){
                long orderId=orders.getId();
                //查询订单明细
                List<OrderDetail> orderDetailList = orderDetailMapper.getByOrderId(orderId);
                OrderVO orderVO = new OrderVO();
                BeanUtils.copyProperties(orders,orderVO);
                orderVO.setOrderDetailList(orderDetailList);
                list.add(orderVO);
            }
        }
        return new PageResult(page.getTotal(),list);
    }

    /*
    * 模拟成功支付
    *
    * */
    public void paySuccess(String outTradeNo) {
        // 1. 根据订单号查询订单
        Orders ordersDB = orderMapper.getByNumber(outTradeNo);

        // 2. 根据订单 id，更新订单状态、支付方式、支付状态、结账时间
        Orders orders = Orders.builder()
                .id(ordersDB.getId())
                .status(Orders.TO_BE_CONFIRMED) // 状态改为：待接单 (2)
                .payStatus(Orders.PAID)         // 支付状态改为：已支付 (1)
                .checkoutTime(LocalDateTime.now()) // 结账时间
                .build();

        orderMapper.update(orders);

        //通过websocket推送消息给客户端 type openid content
        Map map=new HashMap();
        map.put("type",1);
        map.put("oderId",ordersDB.getNumber());
        map.put("content","订单号"+outTradeNo);
        String json=JSON.toJSONString(map);
        webSocketServer.sendToAllClient(json);
    }

    /*
    * 查询订单详细信息
    * @param id
    * @return
    * */
    public OrderVO getOrderDetail(Long id) {
        List<OrderDetail> orderDetaillist = orderDetailMapper.getByOrderId(id);
        Orders orders = orderMapper.getOrder(id);
        OrderVO orderVO = new OrderVO();
        BeanUtils.copyProperties(orders,orderVO);
        orderVO.setOrderDetailList(orderDetaillist);
        // 1. 利用 Stream 流把菜品名和份量拼起来
        String dishStr = orderDetaillist.stream()
                .map(x -> x.getName() + "*" + x.getNumber() + ";")
                .collect(Collectors.joining(" "));

        // 2. 把它塞进 VO 里那个数据库没有的字段中
        orderVO.setOrderDishes(dishStr);
        return orderVO;
    }

    /*
    * 取消订单
    * @param id
    * @return
    * */
    public void cancel(Long id) {
        Orders ordersDB = orderMapper.getById(id);
        // 校验订单是否存在
        if (ordersDB == null) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }

        //订单状态 1待付款 2待接单 3已接单 4派送中 5已完成 6已取消
        if (ordersDB.getStatus() > 2) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }
        Orders orders = new Orders();
        orders.setId(ordersDB.getId());

        // 订单处于待接单状态下取消，需要进行退款
        if (ordersDB.getStatus().equals(Orders.TO_BE_CONFIRMED)) {
/*
            //调用微信支付退款接口
            weChatPayUtil.refund(
                    ordersDB.getNumber(), //商户订单号
                    ordersDB.getNumber(), //商户退款单号
                    new BigDecimal(0.01),//退款金额，单位 元
                    new BigDecimal(0.01));//原订单金额
*/

            //支付状态修改为 退款
            orders.setPayStatus(Orders.REFUND);
        }

        // 更新订单状态、取消原因、取消时间
        orders.setStatus(Orders.CANCELLED);
        orders.setCancelReason("用户取消");
        orders.setCancelTime(LocalDateTime.now());
        orderMapper.update(orders);
    }

    /*
    * 再来一单
    * @param id
    * @return
    * */
    public void repetition(Long id) {
        // 查询当前用户id
        Long userId = BaseContext.getCurrentId();

        // 根据订单id查询当前订单详情
        List<OrderDetail> orderDetailList = orderDetailMapper.getByOrderId(id);

        // 将订单详情对象转换为购物车对象
        List<ShoppingCart> shoppingCartList = orderDetailList.stream().map(x -> {
            ShoppingCart shoppingCart = new ShoppingCart();

            // 将原订单详情里面的菜品信息重新复制到购物车对象中
            BeanUtils.copyProperties(x, shoppingCart, "id");
            shoppingCart.setUserId(userId);
            shoppingCart.setCreateTime(LocalDateTime.now());

            return shoppingCart;
        }).collect(Collectors.toList());

        // 将购物车对象批量添加到数据库
        shoppingCartMapper.insertBatch(shoppingCartList);
    }

    /*
    * 商家订单查询
    * @param ordersPageQueryDTO
    * @return
    * */
    public PageResult conditionSearch(OrdersPageQueryDTO ordersPageQueryDTO) {
        PageHelper.startPage(ordersPageQueryDTO.getPage(),ordersPageQueryDTO.getPageSize());
        Page<Orders> page=orderMapper.pageQuery(ordersPageQueryDTO);
        List<OrderVO> list = new ArrayList<>();
        //查出订单明细，并封装入OrderVO进行响应
        if(page!=null&&page.getTotal()>0){
            for(Orders orders : page){
                long orderId=orders.getId();
                //查询订单明细
                List<OrderDetail> orderDetailList = orderDetailMapper.getByOrderId(orderId);
                OrderVO orderVO = new OrderVO();
                BeanUtils.copyProperties(orders,orderVO);
                orderVO.setOrderDetailList(orderDetailList);
                list.add(orderVO);
            }
        }
        return new PageResult(page.getTotal(),list);
    }

    /*
    * 统计订单数据
    * @return
    * */
    public OrderStatisticsVO statistics() {
        OrderStatisticsVO orderStatisticsVO = new OrderStatisticsVO();
        orderStatisticsVO.setToBeConfirmed(orderMapper.countStatus(Orders.TO_BE_CONFIRMED));
        orderStatisticsVO.setConfirmed(orderMapper.countStatus(Orders.CONFIRMED));
        orderStatisticsVO.setDeliveryInProgress(orderMapper.countStatus(Orders.DELIVERY_IN_PROGRESS));
        return null;
    }



    /*
    * 接受订单
    * @param id
    * @return
    * */
    public void confirm(OrdersConfirmDTO ordersConfirmDTO) {
        Orders orders = Orders.builder()
                .id(ordersConfirmDTO.getId())
                .status(Orders.CONFIRMED) // 状态改为：待接单 (2)
                .build();
        orderMapper.update(orders);
    }

    /*
    * 拒单
    *
    * */
    public void rejection(OrdersRejectionDTO ordersRejectionDTO) {

        //业务规则：
        //
        //- 商家拒单其实就是将订单状态修改为“已取消”
        //- 只有订单处于“待接单”状态时可以执行拒单操作
        //- 商家拒单时需要指定拒单原因
        //- 商家拒单时，如果用户已经完成了支付，需要为用户退款
        // 1. 获取订单（注意判空逻辑）
        Orders ordersDB = orderMapper.getById(ordersRejectionDTO.getId());
        if (ordersDB == null || !ordersDB.getStatus().equals(Orders.TO_BE_CONFIRMED)) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }

        // 2. 处理退款
        Integer payStatus = ordersDB.getPayStatus();
        if (Orders.PAID.equals(payStatus)) {
            // 模拟/真实退款逻辑
            log.info("申请退款操作...");
        }

        // 3. 构造干净的更新对象（只传需要改的）
        Orders orders = Orders.builder()
                .id(ordersDB.getId())
                .status(Orders.CANCELLED)
                .rejectionReason(ordersRejectionDTO.getRejectionReason())
                .cancelTime(LocalDateTime.now())
                .payStatus(Orders.REFUND) // 记得带上支付状态的变更！
                .build();

        orderMapper.update(orders);

    }

    /*
    * 商家取消订单
    * */
    public void cancelAdmin(OrdersCancelDTO ordersCancelDTO) throws Exception{
        Orders ordersDB = orderMapper.getById(ordersCancelDTO.getId());
        Integer payStatus = ordersDB.getPayStatus();
        if(payStatus.equals(Orders.PAID)){
            log.info("用户已支付，需要退款");
            ordersDB.setPayStatus(Orders.REFUND);
        }
        //管理端取消订单需要退款，根据订单id更新订单状态、取消原因、取消时间
        Orders orders=Orders.builder()
                .cancelTime(LocalDateTime.now())
                .cancelReason(ordersCancelDTO.getCancelReason())
                .id(ordersCancelDTO.getId())
                .status(Orders.CANCELLED)
                .build();
        orderMapper.update(orders);
    }

    /*
    * 派送订单
    * @param id
    * @return
    * */
    public void delivery(Long id) {
        //
        //- 派送订单其实就是将订单状态修改为“派送中”
       // - 只有状态为“待派送”的订单可以执行派送订单操作

        Orders ordersDB = orderMapper.getById(id);
        if (!ordersDB.getStatus().equals(Orders.CONFIRMED)) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }
        Orders orders = Orders.builder()
                .id(id)
                .status(Orders.DELIVERY_IN_PROGRESS)
                .deliveryTime(LocalDateTime.now())
                .build();
        orderMapper.update(orders);
    }

    /*
    * 完成订单
    * @param id
    * @return
    * */
    public void complete(Long id) {
        //业务规则：
        //
        //- 完成订单其实就是将订单状态修改为“已完成”
        //- 只有状态为“派送中”的订单可以执行订单完成操作
        Orders ordersDB = orderMapper.getById(id);
        if (!ordersDB.getStatus().equals(Orders.DELIVERY_IN_PROGRESS)) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }
        Orders orders = Orders.builder()
                .id(id)
                .status(Orders.COMPLETED)
                .deliveryTime(LocalDateTime.now())
                .build();
        orderMapper.update(orders);
    }

    /*
    * 客户催单
    * @param id
    * @return
    * */
    public void reminder(Long id) {

        //根据id查询订单
        Orders ordersDB = orderMapper.getById(id);
        if(ordersDB==null){
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }
        Map map=new HashMap();
        map.put("type",2);
        map.put("orderId",id);
        map.put("content","订单号："+ordersDB.getNumber());
        webSocketServer.sendToAllClient(JSON.toJSONString(map));
    }

    /**
     * 检查客户的收货地址是否超出配送范围
     * @param address
     */
    private void checkOutOfRange(String address) {
        Map map = new HashMap();
        map.put("address",shopAddress);
        map.put("output","json");
        map.put("ak",ak);

        //获取店铺的经纬度坐标
        String shopCoordinate = HttpClientUtil.doGet("https://api.map.baidu.com/geocoding/v3", map);

        JSONObject jsonObject = JSON.parseObject(shopCoordinate);
        if(!jsonObject.getString("status").equals("0")){
            throw new OrderBusinessException("店铺地址解析失败");
        }

        //数据解析
        JSONObject location = jsonObject.getJSONObject("result").getJSONObject("location");
        String lat = location.getString("lat");
        String lng = location.getString("lng");
        //店铺经纬度坐标
        String shopLngLat = lat + "," + lng;

        map.put("address",address);
        //获取用户收货地址的经纬度坐标
        String userCoordinate = HttpClientUtil.doGet("https://api.map.baidu.com/geocoding/v3", map);

        log.info("百度地图返回原始数据：{}", userCoordinate);
        jsonObject = JSON.parseObject(userCoordinate);
        if(!jsonObject.getString("status").equals("0")){
            throw new OrderBusinessException("收货地址解析失败");
        }

        //数据解析
        location = jsonObject.getJSONObject("result").getJSONObject("location");
        lat = location.getString("lat");
        lng = location.getString("lng");
        //用户收货地址经纬度坐标
        String userLngLat = lat + "," + lng;

        map.put("origin",shopLngLat);
        map.put("destination",userLngLat);
        map.put("steps_info","0");

        //路线规划
        String json = HttpClientUtil.doGet("https://api.map.baidu.com/directionlite/v1/driving", map);

        jsonObject = JSON.parseObject(json);
        if(!jsonObject.getString("status").equals("0")){
            throw new OrderBusinessException("配送路线规划失败");
        }

        //数据解析
        JSONObject result = jsonObject.getJSONObject("result");
        JSONArray jsonArray = (JSONArray) result.get("routes");
        Integer distance = (Integer) ((JSONObject) jsonArray.get(0)).get("distance");

        if(distance > 5000){
            //配送距离超过5000米
            log.info("【测试用】虽然远，但我假装没看见");
            // throw new OrderBusinessException("超出配送范围"); // 先注释掉
        }
    }


}
