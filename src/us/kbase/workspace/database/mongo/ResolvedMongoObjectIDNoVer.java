package us.kbase.workspace.database.mongo;

import static us.kbase.workspace.database.ObjectIDNoWSNoVer.checkObjectName;

/**
 * name is resolved *at the time the database was accessed and is not further
 * updated*
 * 
 * The underlying assumption of this class is all object IDs are unique and all
 * names are unique at the time of resolution. Therefore a set of
 * ResolvedObjectIDs constructed at the same time are all unique in name and id,
 * and removing one or the other field would not cause the number of unique
 * objects to change (as measured by the unique hashcode count, for example).
 * 
 * This is *not* the case for objects generated from different queries.
 * 
 * @author gaprice@lbl.gov
 *
 */
public class ResolvedMongoObjectIDNoVer {
	
	private final ResolvedMongoWSID rwsi;
	private final String name;
	private final Long id;
	
	ResolvedMongoObjectIDNoVer(final ResolvedMongoWSID rwsi, final String name,
			final long id) {
		if (rwsi == null) {
			throw new IllegalArgumentException("rwsi cannot be null");
		}
		if (id < 1) {
			throw new IllegalArgumentException("id must be > 0");
		}
		checkObjectName(name);
		this.rwsi = rwsi;
		this.name = name;
		this.id = id;
	}
	
	ResolvedMongoObjectIDNoVer(final ResolvedMongoObjectID rmoid) {
		if (rmoid == null) {
			throw new IllegalArgumentException("rmoid cannot be null");
		}
		this.rwsi = rmoid.getWorkspaceIdentifier();
		this.name = rmoid.getName();
		this.id = rmoid.getId();
	}
	
	public ResolvedMongoWSID getWorkspaceIdentifier() {
		return rwsi;
	}

	public String getName() {
		return name;
	}

	public Long getId() {
		return id;
	}

	@Override
	public String toString() {
		return "ResolvedMongoObjectIDNoVer [rwsi=" + rwsi + ", name=" + name
				+ ", id=" + id + "]";
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((id == null) ? 0 : id.hashCode());
		result = prime * result + ((name == null) ? 0 : name.hashCode());
		result = prime * result + ((rwsi == null) ? 0 : rwsi.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (!(obj instanceof ResolvedMongoObjectIDNoVer)) {
			return false;
		}
		ResolvedMongoObjectIDNoVer other = (ResolvedMongoObjectIDNoVer) obj;
		if (id == null) {
			if (other.id != null) {
				return false;
			}
		} else if (!id.equals(other.id)) {
			return false;
		}
		if (name == null) {
			if (other.name != null) {
				return false;
			}
		} else if (!name.equals(other.name)) {
			return false;
		}
		if (rwsi == null) {
			if (other.rwsi != null) {
				return false;
			}
		} else if (!rwsi.equals(other.rwsi)) {
			return false;
		}
		return true;
	}
	
}
