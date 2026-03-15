package com.sky.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.sky.constant.MessageConstant;
import com.sky.constant.StatusConstant;
import com.sky.dto.SetmealDTO;
import com.sky.dto.SetmealPageQueryDTO;
import com.sky.entity.Setmeal;
import com.sky.entity.SetmealDish;
import com.sky.exception.DeletionNotAllowedException;
import com.sky.mapper.SetmealDishMapper;
import com.sky.mapper.SetmealMapper;
import com.sky.result.PageResult;
import com.sky.result.Result;
import com.sky.service.SetmealService;
import com.sky.vo.DishItemVO;
import com.sky.vo.SetmealVO;
import io.lettuce.core.dynamic.annotation.Param;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class SetmealServiceImpl implements SetmealService {
    @Autowired
    private SetmealMapper setmealMapper;
    @Autowired
    private SetmealDishMapper setmealDishMapper;
    /*
    * 添加套餐
    * @param dishIds
    * @return
    * */
    public void saveWithDish(SetmealDTO setmealDTO) {
        System.out.println("当前线程Id:"+Thread.currentThread().getId());
        Setmeal setmeal = new Setmeal();
        BeanUtils.copyProperties(setmealDTO,setmeal);
        //设置套餐状态，默认正常，1正常，0锁定
        setmeal.setStatus(StatusConstant.ENABLE);
        //先插入套餐表
        setmealMapper.insert(setmeal);

        //获取刚插入的套餐的id
        Long id = setmeal.getId();
        List<SetmealDish> setmealDishes = setmealDTO.getSetmealDishes();
        //再插入套餐菜品关系表
        if(setmealDishes!=null && setmealDishes.size()>0){
            // 【关键逻辑】为每一个关联对象设置所属的套餐 ID
            setmealDishes.forEach(setmealDish -> {
                setmealDish.setSetmealId(id);
            });
            setmealDishMapper.insertBatch(setmealDishes);
        }

    }

    /*
    * 菜品分页查询
    * @param page
    * @param pageSize
    * @param name
    * @return
    * */
    public PageResult pageQuery(SetmealPageQueryDTO setmealPageQueryDTO) {
        PageHelper.startPage(setmealPageQueryDTO.getPage(),setmealPageQueryDTO.getPageSize());
        Page<SetmealVO> page= setmealMapper.pageQuery(setmealPageQueryDTO);
        return new PageResult(page.getTotal(),page.getResult());
    }

    /*
    * 根据id查询套餐
    * */
    public SetmealVO getById(Long setmealid) {
        Setmeal setmeal=setmealMapper.getBysetmealId(setmealid);
        List<SetmealDish> setmealDishes=setmealDishMapper.getBySetmealId(setmealid);
        SetmealVO setmealVO=new SetmealVO();
        BeanUtils.copyProperties(setmeal,setmealVO);
        setmealVO.setSetmealDishes(setmealDishes);
        return setmealVO;
    }

    /*
    * 修改套餐
    * @param setmealDTO
    * @return
    * */
    @Transactional
    public void update(SetmealDTO setmealDTO) {
        Setmeal setmeal = new Setmeal();
        BeanUtils.copyProperties(setmealDTO,setmeal);
        setmealMapper.update(setmeal);
        //删除套餐关联的菜品数据
        setmealDishMapper.deleteBySetmealId(setmealDTO.getId());
        //插入新的菜品数据
        List<SetmealDish> setmealDishes= setmealDTO.getSetmealDishes();
        if(setmealDishes != null && setmealDishes.size() > 0){
            setmealDishes.forEach(setmealDish -> {
                setmealDish.setSetmealId(setmealDTO.getId());
            });
            setmealDishMapper.insertBatch(setmealDishes);
        }
    }

    /*
    * 批量删除
    * @param ids
    * @return
    * */
    public void delete(@Param("ids") List<Long> ids) {
        //判断当前套餐是否可以删除
        for(Long id:ids){
            Setmeal setmeal=setmealMapper.getBysetmealId(id);
            if(setmeal.getStatus()==1){
                throw new DeletionNotAllowedException(MessageConstant.SETMEAL_ON_SALE);
            }
        }
        //先删除套餐表中的数据

        setmealMapper.deleteAll(ids);
        //再删除套餐菜品关系表
        setmealDishMapper.deleteSetmealDishAll(ids);

    }

    /*
    * 设置套餐起售停售
    * @param status
    * @param id
    * @return
    * */
    public void startOrStop(Integer status, Long id) {
        Setmeal setmeal= Setmeal.builder()
                .id(id)
                .status(status)
                .build();
        setmealMapper.update(setmeal);

    }

    /*
    * 查询套餐
    * @param setmeal
    * @return
    * */
    public List<Setmeal> list(Setmeal setmeal) {
        List<Setmeal> list = setmealMapper.list(setmeal);
        return list;
    }

    @Override
    public List<DishItemVO> getDishItemById(Long id) {
        return setmealMapper.getDishItemBySetmealId(id);
    }


}
