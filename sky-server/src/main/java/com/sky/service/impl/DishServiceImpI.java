package com.sky.service.impl;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.sky.constant.MessageConstant;
import com.sky.constant.StatusConstant;
import com.sky.dto.DishDTO;
import com.sky.dto.DishPageQueryDTO;
import com.sky.entity.Dish;
import com.sky.entity.DishFlavor;
import com.sky.entity.Setmeal;
import com.sky.exception.DeletionNotAllowedException;
import com.sky.mapper.DishFlavorMapper;
import com.sky.mapper.DishMapper;
import com.sky.mapper.SetmealMapper;
import com.sky.result.PageResult;
import com.sky.service.DishService;
import com.sky.vo.DishVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
@Slf4j
@Transactional
public class DishServiceImpI implements DishService {
    /**
     * 新增菜品
     * @param dishDTO
     */

    @Autowired
    private DishMapper dishMapper;

    @Autowired
    private DishFlavorMapper dishFlavorMapper;

    @Autowired
    private SetmealMapper setmealMapper;

    /**
     * 新增菜品
     * @param dishDTO
     */
    @Override
    public void saveWithFlavor(DishDTO dishDTO) {
        Dish dish = new Dish();

        BeanUtils.copyProperties(dishDTO, dish);

        // 向菜品中插入一条数据
        dishMapper.insert(dish);

        // 获得插入的主键id
        Long dishId = dish.getId();

        // 获取到口味数据
        List<DishFlavor> flavors = dishDTO.getFlavors();

        // 遍历口味数据，为口味数据设置菜品id
        if(flavors != null && flavors.size() > 0){
            for (DishFlavor flavor : flavors) {
                flavor.setDishId(dishId);
            }
        }
        dishFlavorMapper.insertBatch(flavors);
    }


    /**
     * 菜品分页查询
     * @param dishPageQueryDTO
     * @return
     */
    @Override
    public PageResult pageQuery(DishPageQueryDTO dishPageQueryDTO) {
        PageHelper.startPage(dishPageQueryDTO.getPage(), dishPageQueryDTO.getPageSize());

        Page<DishVO> page = dishMapper.pageQuery(dishPageQueryDTO);
        return new PageResult(page.getTotal(), page.getResult());
    }

    /**
     * 批量删除菜品
     * @param ids
     */
    @Override
    public void deleteBatch(List<Long> ids) {
        // 判断当前菜品是否能够删除
        for (Long id : ids) {
            Dish dish = dishMapper.getById(id); // 根据id查询菜品

            if (dish.getStatus() == StatusConstant.ENABLE) {
                // 当前菜品售卖中无法被删除
                throw new DeletionNotAllowedException(MessageConstant.DISH_ON_SALE);
            }
        }
        // 判断当前菜品是否关联了套餐
        List<Long> setmealIds = setmealMapper.getSetmealIdsByDishIds(ids);
        if(setmealIds != null && setmealIds.size() > 0){
            throw new DeletionNotAllowedException(MessageConstant.DISH_BE_RELATED_BY_SETMEAL);
        }

        // 删除菜品表中的数据
        for(Long id : ids){
            dishMapper.deleteById(id);

            // 删除与之对应的口味表中的数据
            dishFlavorMapper.deleteByDishId(id);
        }

    }

    /**
     * 根据id查询菜品
     * @param id
     * @return
     */
    @Override
    public DishVO getByIdWithFlavor(Long id) {
        // 根据id查询菜品数据
        Dish dish = dishMapper.getById(id);

        // 根绝id查询口味
        List<DishFlavor> dishFlavors = dishFlavorMapper.getByDishId(id);

        DishVO dishVO = new DishVO();
        BeanUtils.copyProperties(dish, dishVO);
        dishVO.setFlavors(dishFlavors);

        return dishVO;
    }

    /**
     * 条件查询菜品和口味
     * @param dish
     * @return
     */
    public List<DishVO> listWithFlavor(Dish dish) {
        List<Dish> dishList = dishMapper.list(dish);

        List<DishVO> dishVOList = new ArrayList<>();

        for (Dish d : dishList) {
            DishVO dishVO = new DishVO();
            BeanUtils.copyProperties(d,dishVO);

            //根据菜品id查询对应的口味
            List<DishFlavor> flavors = dishFlavorMapper.getByDishId(d.getId());

            dishVO.setFlavors(flavors);
            dishVOList.add(dishVO);
        }

        return dishVOList;
    }

    @Override
    public List<Dish> list(Long categoryId) {
        Dish dish = Dish.builder()
                .categoryId(categoryId)
                .status(StatusConstant.ENABLE)
                .build();

        return dishMapper.list(dish);

    }

    /**
     * 修改菜品起售停售
     * @param status
     * @param id
     */
    @Override
    public void updateStatus(Integer status, Long id) {
        // 判断是否能够被停售
        if(status == StatusConstant.ENABLE){
            // 要将与之关联的套餐停售
            List<Long> dishIds = new ArrayList<>();
            dishIds.add(id);

            // 获取到关联的套餐id
            List<Long> setmealIds = setmealMapper.getSetmealIdsByDishIds(dishIds);

            if(setmealIds != null && setmealIds.size() > 0){
                for(Long setmealId : setmealIds){
                    // 修改套餐状态
                    Setmeal setmeal = Setmeal.builder()
                            .id(setmealId)
                            .status(StatusConstant.DISABLE)
                            .build();

                    setmealMapper.update(setmeal);
                }
            }
        }

        Dish dish = Dish.builder()
                .id(id)
                .status(status)
                .build();
        dishMapper.update(dish);

    }

    /**
     * 修改菜品
     * @param dishDTO
     */
    @Transactional
    @Override
    public void updateWithFlavor(DishDTO dishDTO) {
        Dish dish = new Dish();

        BeanUtils.copyProperties(dishDTO, dish);

        //修改菜品表的基本信息
        dishMapper.update(dish);

        //删除原有的口味数据
        dishFlavorMapper.deleteByDishId(dishDTO.getId());

        // 修改菜品对应的口味
        List<DishFlavor> flavors = dishDTO.getFlavors();

        // 判断是否需要修改
        if(flavors != null && flavors.size() > 0){
            for(DishFlavor flavor : flavors){
                flavor.setDishId(dishDTO.getId());
            }
            dishFlavorMapper.insertBatch(flavors);
        }
    }
}
