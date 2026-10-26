package com.antshorttv.script;

import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

@Component
public class ManualAssetConfiguration {
    private final Map<String, Definition> definitions;
    private static final List<String> CHARACTER_ORDER = List.of("era","country","ethnicity","genre","face","hair",
        "earrings","body","ratio","upper","lower","shoes","gender","age");
    private static final Set<String> UNKNOWN = Set.of("未明确","未明确（待确认）","未知","不详","待补充","待确认","未填写","待填写","暂无","不确定","无依据","未明确(待确认)");
    public ManualAssetConfiguration(ObjectMapper json) {
        try (var stream = new ClassPathResource("manual-assets/configuration.json").getInputStream()) {
            definitions = Collections.unmodifiableMap(json.readValue(stream, new TypeReference<Map<String, Definition>>() {}));
        } catch(IOException e) { throw new IllegalStateException("手动资产配置无法加载。",e); }
    }
    public Map<String, Definition> definitions() { return definitions; }
    public Definition definition(String type) {
        var definition = definitions.get(type);
        if(definition == null) throw invalid("请选择角色、场景或道具。");
        return definition;
    }
    public Built build(String type, Map<String,String> input) {
        Definition definition=definition(type);
        if(input == null || !input.keySet().equals(definition.fields().stream().map(Field::key).collect(Collectors.toSet())))
            throw invalid("请完整填写当前资产类型的设定项。");
        Map<String,String> values=new LinkedHashMap<>();
        for(var field:definition.fields()) {
            String value=Optional.ofNullable(input.get(field.key())).orElse("").trim();
            if(value.isEmpty() || value.length()>field.maxLength()) throw invalid(field.label()+"不能为空且不能超过 "+field.maxLength()+" 字符。");
            if(AssetIdentityNormalizer.normalize(value).isEmpty() || value.matches("(?s).*\\{[^}]*}.*") || value.contains("[待填写") ||
                (UNKNOWN.contains(value) && !value.equals(field.unknown()))) throw invalid("请为"+field.label()+"填写具体设定。");
            values.put(field.key(),value);
        }
        String description=definition.fields().stream().map(f->f.label()+"："+values.get(f.key())).collect(Collectors.joining("；"));
        String prompt;
        if("CHARACTER".equals(type)) {
            var labels=definition.fields().stream().collect(Collectors.toMap(Field::key,Field::label));
            prompt=definition.task()+"\n\n角色描述:\n\n"+CHARACTER_ORDER.stream()
                .map(key->"- **"+labels.get(key)+"**: "+values.get(key)).collect(Collectors.joining("\n"))
                +"\n\n展示状态：自然站立、中性表情、双手自然下垂。";
        } else if("SCENE".equals(type)) prompt=definition.task()+"\n\n场景描述: "+description+"。";
        else prompt=description+"。"+definition.task();
        if(description.length()>4000 || prompt.length()>10000) throw invalid("设定内容过长，请适当精简。");
        return new Built(Collections.unmodifiableMap(values),description,prompt);
    }
    static BusinessException invalid(String message) { return new BusinessException(ErrorCode.VALIDATION_ERROR,message); }
    public record Field(String key,String label,String group,String placeholder,String unknown,boolean textarea,int maxLength) {}
    public record Definition(List<Field> fields,String task) {}
    public record Built(Map<String,String> values,String description,String prompt) {}
}
