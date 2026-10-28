package com.antshorttv.storage;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class MediaDeliveryGrantBatchTest {
    private final Instant now=Instant.parse("2026-10-28T06:00:00Z");
    private final MediaDeliveryGrantStore store=mock(MediaDeliveryGrantStore.class);
    private MediaDeliveryGrantService service() {
        var properties=new ObjectStorageProperties();properties.setCdnDomain("https://cdn.example.com");properties.setCdnTypeDKey("test-key");
        return new MediaDeliveryGrantService(store,properties,Clock.fixed(now,ZoneOffset.UTC));
    }
    private DeliveryGrantRequest request(int id,boolean video) {
        return new DeliveryGrantRequest(1L,null,2L,"FREE_CREATION_MEDIA",(long)id,"v1","ORIGINAL","media/"+id,video);
    }
    private MediaDeliveryGrantEntity entity(DeliveryGrantRequest request,long remaining) {
        return MediaDeliveryGrantEntity.create(request,MediaDeliveryGrantService.objectKeyHash(request.objectKey()),now.plusSeconds(remaining),1,now);
    }
    @Test void readsExistingAuthorizationsOnceWithoutRowLocksAndDeduplicatesKeys() {
        var requests=java.util.stream.IntStream.range(0,30).mapToObj(id->request(id,false)).toList();
        when(store.findAll(eq(2L),anyList())).thenReturn(requests.stream().map(r->entity(r,86400)).toList());
        var duplicate=new ArrayList<>(requests);duplicate.add(requests.get(0));
        var result=service().issueAll(duplicate);
        assertThat(result).hasSize(30);
        assertThat(result.get("media/0").expiresAt()).isEqualTo(now.plusSeconds(86400));
        verify(store).findAll(eq(2L),argThat(hashes->hashes.size()==30));
        verify(store,never()).find(any(),any());verify(store,never()).insert(any());verify(store,never()).update(any());
    }
    @Test void renewsVideoNearExpiryUsingTheLockedSingleItemPath() {
        var request=request(1,true);var old=entity(request,100);
        when(store.findAll(eq(2L),anyList())).thenReturn(List.of(old));
        when(store.find(2L,old.objectKeyHash)).thenReturn(old);
        var result=service().issueAll(List.of(request(1,false),request));
        assertThat(result.get(request.objectKey()).expiresAt()).isEqualTo(now.plusSeconds(604800));
        verify(store).find(2L,old.objectKeyHash);verify(store).update(old);
    }
    @Test void createsMissingGrantsAndKeepsTheSingleRequestRaceHandling() {
        var request=request(1,false);
        when(store.findAll(eq(2L),anyList())).thenReturn(List.of());
        var result=service().issueAll(List.of(request,request));
        assertThat(result).hasSize(1);verify(store).insert(any());
    }
    @Test void rejectsMixedUsersAndTenantsBeforeAnyRead() {
        var foreignUser=new DeliveryGrantRequest(1L,null,3L,"FREE_CREATION_MEDIA",1L,"v1","ORIGINAL","media/1",false);
        var foreignTenant=new DeliveryGrantRequest(9L,null,2L,"FREE_CREATION_MEDIA",1L,"v1","ORIGINAL","media/1",false);
        assertThatThrownBy(()->service().issueAll(List.of(request(1,false),foreignUser))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->service().issueAll(List.of(request(1,false),foreignTenant))).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(store);
    }
    @Test void doesNothingForEmptyBatches() {
        assertThat(service().issueAll(List.of())).isEmpty();verifyNoInteractions(store);
    }
}
