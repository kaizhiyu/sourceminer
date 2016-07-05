/**
 * 
 */
package br.com.jvoliveira.sourceminer.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import br.com.jvoliveira.arq.controller.AbstractArqController;
import br.com.jvoliveira.sourceminer.domain.CaseMetricItem;
import br.com.jvoliveira.sourceminer.domain.Metric;
import br.com.jvoliveira.sourceminer.domain.MetricCase;
import br.com.jvoliveira.sourceminer.service.MetricCaseService;

/**
 * @author Joao Victor
 *
 */
@Controller
@RequestMapping("/metric_case")
public class MetricCaseController extends AbstractArqController<MetricCase>{

	@Autowired
	public MetricCaseController(MetricCaseService service){
		this.service = service;
		this.path = "metric_case";
		this.title = "Case de Métricas";
	}
	
	@ModelAttribute("metrics")
	public List<Metric> getAllMetrics(){
		return ((MetricCaseService)this.service).getAllMetrics();
	}
	
	@ResponseBody
	@RequestMapping(value = "/add_metric_case_item", method = RequestMethod.POST)
	public String addMetricCaseItem(@RequestParam String idMetric, @RequestParam String valueMetric){
		CaseMetricItem caseMetric = new CaseMetricItem();
		
		Metric metric = ((MetricCaseService)this.service).getMetric(Long.parseLong(idMetric));
		
		caseMetric.setMetric(metric);
		caseMetric.setThreshold(Double.parseDouble(valueMetric));
		
		this.obj.getCaseMetricItems().add(caseMetric);
		return "";
	}
}
