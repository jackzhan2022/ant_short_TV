package com.antshorttv.commercial;
import com.baomidou.mybatisplus.core.mapper.BaseMapper; import org.apache.ibatis.annotations.Mapper;
@Mapper interface CommercialPackageMapper extends BaseMapper<CommercialPackageEntity> {}
@Mapper interface CommercialPackageVersionMapper extends BaseMapper<CommercialPackageVersionEntity> {}
@Mapper interface CommercialEntitlementMapper extends BaseMapper<CommercialEntitlementEntity> {}
@Mapper interface CommercialOrderMapper extends BaseMapper<CommercialOrderEntity> {}
@Mapper interface CommercialPaymentMapper extends BaseMapper<CommercialPaymentEntity> {}
@Mapper interface TeamSubscriptionMapper extends BaseMapper<TeamSubscriptionEntity> {}
@Mapper interface CommercialEntitlementGrantMapper extends BaseMapper<CommercialEntitlementGrantEntity> {}
@Mapper interface CommercialPaymentEventMapper extends BaseMapper<CommercialPaymentEventEntity> {}
@Mapper interface CommercialAuditMapper extends BaseMapper<CommercialAuditEntity> {}
