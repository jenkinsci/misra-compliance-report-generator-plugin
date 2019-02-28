package rortveiten.misra;

import java.util.ArrayList;
import java.util.List;

public class Guideline {
	
	public static enum Category {
		MANDATORY("Mandatory"),
		REQUIRED("Required"),
		ADVISORY("Advisory"),
		DISAPPLIED("Disapplied"),
		UNKNOWN("Unknown");
	
		private final String value;
		
		private Category(String value) {this.value = value;}
		
		@Override
		public String toString() { return value; }
		
		public static Category fromString(String value) {
			if (value == null)
				return null;
			switch (value.toUpperCase())
			{
			case "MANDATORY":
			case "MAND":
				return MANDATORY;
			case "REQUIRED":
			case "REQ":
				return REQUIRED;
			case "ADVISORY":
			case "ADV":
				return ADVISORY;
			case "DISAPPLIED":
			case "DIS":
				return DISAPPLIED;
			}
			return UNKNOWN;
		}
	};
	
	public static class DeviationReference {
        private String reference;
        private String link;
        
	    public DeviationReference(String reference, String link) {
	        this.reference = reference;
	        this.link = link;
	    }
	    
	    public String getReference() {
	        return reference;
	    }
	    
	    public String getLink() {
	        return link;
	    }
	}
	
	public static enum ComplianceStatus {
		COMPLIANT,
		VIOLATIONS,
		DEVIATIONS,
		DISAPPLIED
	}
	
	private String id;
	private Category category;
	private Category reCategorization;
	private String toolsUsedToCheckRequirement;
	private ComplianceStatus status;
	private List<DeviationReference> deviationReferences;
	
	public Guideline(String id)
	{
		this.id = id;
		status = ComplianceStatus.COMPLIANT;
		deviationReferences = new ArrayList<DeviationReference>(1);
	}
	
	public Guideline(String id, String category) {
		this(id);
		this.setCategory(category);
	}
	
	public Guideline(String id, 
			Category category, Category reCategorization,
			String toolsUsedToCheckRequirement)
	{
		this(id);
		this.setCategory(category);
		this.setReCategorization(reCategorization);
		this.setToolsUsedToCheckRequirement(toolsUsedToCheckRequirement);
	}
	
	public Guideline(String id, 
			String category, String reCategorization,
			String toolsUsedToCheckRequirement)
	{
		this(id, Category.fromString(category),
				Category.fromString(reCategorization),
				toolsUsedToCheckRequirement);
	}

	
	
	@Override
	public String toString()
	{
		return id;
	}

	
	public Category getCategory() {
		return category;
	}
	
	public String getCategoryString() {
		return category.toString();
	}


	public Category getReCategorization() {
		return reCategorization;
	}
	
	public String getReCategorizationString() {
		if (reCategorization != null)
			return reCategorization.toString();
		return null;
	}

	public String getToolsUsedToCheckRequirement() {
		return toolsUsedToCheckRequirement;
	}

	public void setCategory(Category category) {
		this.category = category;
	}
	
	public void setCategory(String category) {
		this.category = Category.fromString(category);
	}

	public void setReCategorization(Category reCategorization) {
		this.reCategorization = reCategorization;
	      if (this.reCategorization == Category.DISAPPLIED)
	            status = ComplianceStatus.DISAPPLIED;
	}

	public void setReCategorization(String reCategorization) {
		setReCategorization(Category.fromString(reCategorization));
	}
	
	public void setToolsUsedToCheckRequirement(String toolsUsedToCheckRequirement) {
		this.toolsUsedToCheckRequirement = toolsUsedToCheckRequirement;
	}

	public List<DeviationReference> getDeviationReferences() {
		return deviationReferences;
	}

	public void addDeviationReference(String reference, String link) {
	    DeviationReference ref = new DeviationReference(reference, link);
		getDeviationReferences().add(ref);
	}

	public ComplianceStatus getStatus() {
		return status;
	}

	public void setStatus(ComplianceStatus status) {
		this.status = status;
	}
	
	public Category activeCategory() {
		if (reCategorization != null)
			return reCategorization;
		return category;
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}
}
