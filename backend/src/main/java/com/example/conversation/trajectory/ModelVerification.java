package com.example.conversation.trajectory;
import com.example.conversation.AiB2BSaaSService;
import com.fasterxml.jackson.databind.*;
import java.nio.file.*;
import java.util.*;

/** Fails with a nonzero exit code on any token, numerical, or decision mismatch. */
public final class ModelVerification {
    static float[][][] expected(JsonNode node) throws Exception {return AiB2BSaaSService.JSON.treeToValue(node,float[][][].class);}
    static void require(boolean ok,String message) {if(!ok)throw new IllegalStateException(message);}
    static double compare(float[][][] actual,float[][][] expected,double atol,double rtol) {
        require(actual.length==expected.length,"Output count mismatch");double max=0;
        for(int i=0;i<actual.length;i++) {
            require(actual[i].length==expected[i].length,"Batch shape mismatch");
            for(int j=0;j<actual[i].length;j++) {
                require(actual[i][j].length==expected[i][j].length,"Output shape mismatch");
                for(int k=0;k<actual[i][j].length;k++) {
                    double a=actual[i][j][k],b=expected[i][j][k],error=Math.abs(a-b);
                    require(Double.isFinite(a)&&Double.isFinite(b)&&error<=atol+rtol*Math.abs(b),"Numerical mismatch: "+a+" vs "+b);
                    max=Math.max(max,error);
                }
            }
        } return max;
    }
    static double sigmoid(double x) {return 1/(1+Math.exp(-Math.max(-700,Math.min(700,x))));}
    static double calibrated(double x,JsonNode c) {return sigmoid(c.required("slope").asDouble()*x+c.required("intercept").asDouble());}
    public static void main(String[] args) throws Exception {
        if(args.length<1 || args.length>2)throw new IllegalArgumentException("Usage: Verify BUNDLE [THREADS]");
        Path root=Path.of(args[0]);Path reportPath=Path.of(System.getProperty("trajectory.verificationReport", "build/reports/trajectory/verification.json"));
        Files.createDirectories(reportPath.toAbsolutePath().getParent());
        // Remove stale success before starting a new verification attempt.
        Files.deleteIfExists(reportPath);
        if(Boolean.getBoolean("trajectory.requireS390x") && !System.getProperty("os.arch").equals("s390x")) throw new IllegalStateException("Verification requires an s390x JVM");
        try(var model=Boolean.getBoolean("trajectory.verifyResources") ? AiB2BSaaSService.fromResources("trajectory",3) : new AiB2BSaaSService(root,args.length==2?Integer.parseInt(args[1]):3)) {
            JsonNode gold=AiB2BSaaSService.JSON.readTree(root.resolve("golden.json").toFile());
            double atol=model.manifest.required("tolerances").required("atol").asDouble(),rtol=model.manifest.required("tolerances").required("rtol").asDouble();
            var states=new HashMap<String,float[][][]>();var rows=new ArrayList<Map<String,Object>>();
            for(var c:gold.required("encoder")) {
                String id=c.required("id").asText();var tokens=model.tokenize(c.required("text").asText());
                for(var entry:tokens.entrySet())require(Arrays.deepEquals(entry.getValue(),AiB2BSaaSService.JSON.treeToValue(c.required("inputs").required(entry.getKey()),long[][].class)),id+": token mismatch: "+entry.getKey());
                long start=System.nanoTime();var actual=model.encodeTokens(tokens);double seconds=(System.nanoTime()-start)/1e9;
                double error=compare(actual,expected(c.required("expected")),atol,rtol);states.put(id,actual);
                rows.add(Map.of("id",id,"max_abs_error",error,"seconds",seconds));
            }
            for(var c:gold.required("composition")) {
                String id=c.required("id").asText();var left=states.get(c.required("left").asText());var right=states.get(c.required("right").asText());
                var meta=AiB2BSaaSService.JSON.treeToValue(c.required("metadata"),float[][].class)[0];
                var actual=model.compose(left[0][0],right[0][0],meta);
                double error=compare(actual,expected(c.required("expected")),atol,rtol);
                var calibration=gold.required("calibration");var threshold=gold.required("thresholds");
                double before=calibrated(left[1][0][0],calibration.get(0)),after=calibrated(actual[1][0][0],calibration.get(0));
                double link=calibrated(actual[2][0][0],calibration.get(calibration.size()-1));
                require((link>threshold.required("link").asDouble() && after-before>threshold.required("growth").asDouble())==c.required("decision").required("merge").asBoolean(),id+": merge decision mismatch");
                require((after>=threshold.required("risk").asDouble())==c.required("decision").required("alert").asBoolean(),id+": alert decision mismatch");
                states.put(id,actual);rows.add(Map.of("id",id,"max_abs_error",error));
            }
            int rejected=0;
            for(var text:gold.required("invalid_texts")) {
                try {model.tokenize(text.asText());}catch(IllegalArgumentException e){rejected++;continue;}
                throw new IllegalStateException("Invalid text was accepted");
            }
            var report=new LinkedHashMap<String,Object>();report.put("generated_utc",java.time.Instant.now().toString());report.put("threads",args.length==2?Integer.parseInt(args[1]):3);report.put("passed",true);report.put("backend","onnxruntime-java");report.put("loading_mode",Boolean.getBoolean("trajectory.verifyResources")?"classpath-resources":"filesystem");
            report.put("os",System.getProperty("os.name"));report.put("architecture",System.getProperty("os.arch"));report.put("java",System.getProperty("java.version"));
            report.put("manifest_sha256",AiB2BSaaSService.sha256(root.resolve("manifest.json")));report.put("cases",rows);report.put("invalid_texts_rejected",rejected);
            report.put("quality_acceptance",false);report.put("decision_scope",gold.required("decision_scope").asText());
            AiB2BSaaSService.JSON.writerWithDefaultPrettyPrinter().writeValue(reportPath.toFile(),report);
            System.out.println(AiB2BSaaSService.JSON.writerWithDefaultPrettyPrinter().writeValueAsString(report));
        }
    }
}
