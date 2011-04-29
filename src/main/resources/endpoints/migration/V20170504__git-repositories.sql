alter table application add revision varchar not null;
update application set revision = svn_revision;
alter table application drop svn_revision;
