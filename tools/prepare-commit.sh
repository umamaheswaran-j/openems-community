#!/bin/bash -e
#
# Prepares a Commit
#
# - Adds .gitignore file to empty test directories.
# 
#   When Eclipse creates 'test' src folders they are sometimes empty. Empty 
#   folders are not committed to GIT. Because of this Eclipse would show errors
#   when importing the projects. This script creates an empty '.gitignore' file
#   inside each 'test' folder to solve this.
#
#   See https://stackoverflow.com/questions/115983
# 
# - Resets .classpath files.
#
#   When Eclipse 'Build All' is called, all .classpath files are touched and
#   unnecessarily marked as changed. Using this script those files are reset
#   to origin.
#
# - Resolves EdgeApp and BackendApp bndrun files
#

# Check bundles
for D in *; do
	if [ -d "${D}" ]; then
		case "${D}" in
			build|cnf|doc|edge|ui|tools)
				;;
			*)

				# check for empty/non-project directories
				if [ ! -d "${D}/src" ]; then
					echo "${D} is empty. Delete directory?"
					select yn in "Yes" "No"; do
						case $yn in
							Yes ) rm -rf "${D}"; break;;
							No ) ;;
						esac
					done
					continue
				fi

				echo "# preparing " ${D}

				# verify the project .gitignore file
				if grep -q '/bin_test/' ${D}/.gitignore \
					&& grep -q '/generated/' ${D}/.gitignore; then
					:
				else
					echo "${D}/.gitignore -> not complete"
					echo '/bin_test/' > ${D}/.gitignore
					echo '/generated/' >> ${D}/.gitignore
				fi 
		
				# verify there is a test folder
				if [ ! -d "${D}/test" ]; then
					mkdir -p ${D}/test
				fi
				
				# verify that the test folder has a .gitignore file
				if [ ! -f "./${D}/test/.gitignore" ]; then
					echo "${D}/test/.gitignore -> missing"
					touch ${D}/test/.gitignore
				fi

				# Set default .classpath file
				if [ -f "${D}/.classpath" ]; then
					cat <<EOT > "${D}/.classpath"
<?xml version="1.0" encoding="UTF-8"?>
<classpath>
	<classpathentry kind="con" path="aQute.bnd.classpath.container"/>
	<classpathentry kind="con" path="org.eclipse.jdt.launching.JRE_CONTAINER/org.eclipse.jdt.internal.debug.ui.launcher.StandardVMType/JavaSE-11"/>
	<classpathentry kind="src" output="bin" path="src"/>
	<classpathentry kind="src" output="bin_test" path="test">
		<attributes>
			<attribute name="test" value="true"/>
		</attributes>
	</classpathentry>
	<classpathentry kind="output" path="bin"/>
</classpath>
EOT
				fi

				# Remove .settings directory
				if [ -d "${D}/.settings" ]; then
					rm -R "${D}/.settings"
				fi

				# Verify bnd.bnd file
				if [ -f "${D}/bnd.bnd" ]; then
					start=$(grep -n '${buildpath},' "${D}/bnd.bnd" | grep -Eo '^[^:]+' | head -n1)
					end=$(grep -n 'testpath' "${D}/bnd.bnd" | grep -Eo '^[^:]+' | head -n1)
					if [ -z "$start" -a -z "$end" ]; then
						:
					else
						(
							head -n $start "${D}/bnd.bnd"; # before 'buildpath'
							head -n$(expr $end - 2) "${D}/bnd.bnd" | tail -n$(expr $end - $start - 2) | sort; # the 'buildpath'
							tail -n +$(expr $end - 1) "${D}/bnd.bnd" # after 'buildpath'
						) > "${D}/bnd.bnd.new"
						if [ $? -eq 0 ]; then
							mv "${D}/bnd.bnd.new" "${D}/bnd.bnd"
						else
							echo "Unable to sort buildpath in ${D}/bnd.bnd"
							exit 1
						fi
					fi
				fi
				;;
		esac
	fi
done

# Build
./gradlew build

# Update EdgeApp.bndrun
bndrun='io.openems.edge.application/EdgeApp.bndrun'
head -n $(grep -n '\-runrequires:' $bndrun | grep -Eo '^[^:]+' | head -n1) "$bndrun" > "$bndrun.new"
echo "	bnd.identity;id='org.ops4j.pax.logging.pax-logging-api',\\" >> "$bndrun.new"
echo "	bnd.identity;id='org.ops4j.pax.logging.pax-logging-log4j1',\\" >> "$bndrun.new"
echo "	bnd.identity;id='org.apache.felix.http.jetty',\\" >> "$bndrun.new"
echo "	bnd.identity;id='org.apache.felix.webconsole',\\" >> "$bndrun.new"
echo "	bnd.identity;id='org.apache.felix.webconsole.plugins.ds',\\" >> "$bndrun.new"
echo "	bnd.identity;id='org.apache.felix.inventory',\\" >> "$bndrun.new"
for D in io.openems.edge.*; do
	if [[ "$D" == *api ]]; then
		continue # ignore api bundle
	fi
	echo "	bnd.identity;id='${D}',\\" >> "$bndrun.new"
done
runbundles=$(grep -n '\-runbundles:' $bndrun | grep -Eo '^[^:]+' | head -n1)
tail -n +$(expr $runbundles - 1) "$bndrun" >> "$bndrun.new"
head -n $(grep -n '\-runbundles:' "$bndrun.new" | grep -Eo '^[^:]+' | head -n1) "$bndrun.new" > "$bndrun"
rm "$bndrun.new"
./gradlew resolve.EdgeApp

# Update BackendApp.bndrun
bndrun='io.openems.backend.application/BackendApp.bndrun'
head -n $(grep -n '\-runrequires:' $bndrun | grep -Eo '^[^:]+' | head -n1) "$bndrun" > "$bndrun.new"
echo "	bnd.identity;id='org.ops4j.pax.logging.pax-logging-api',\\" >> "$bndrun.new"
echo "	bnd.identity;id='org.ops4j.pax.logging.pax-logging-log4j1',\\" >> "$bndrun.new"
echo "	bnd.identity;id='org.osgi.service.jdbc',\\" >> "$bndrun.new"
echo "	bnd.identity;id='org.apache.felix.http.jetty',\\" >> "$bndrun.new"
echo "	bnd.identity;id='org.apache.felix.webconsole',\\" >> "$bndrun.new"
echo "	bnd.identity;id='org.apache.felix.webconsole.plugins.ds',\\" >> "$bndrun.new"
echo "	bnd.identity;id='org.apache.felix.inventory',\\" >> "$bndrun.new"
for D in io.openems.backend.*; do
	if [[ "$D" == *api ]]; then
		continue # ignore api bundle
	fi
	echo "	bnd.identity;id='${D}',\\" >> "$bndrun.new"
done
runbundles=$(grep -n '\-runbundles:' $bndrun | grep -Eo '^[^:]+' | head -n1)
tail -n +$(expr $runbundles - 1) "$bndrun" >> "$bndrun.new"
head -n $(grep -n '\-runbundles:' "$bndrun.new" | grep -Eo '^[^:]+' | head -n1) "$bndrun.new" > "$bndrun"
rm "$bndrun.new"
./gradlew resolve.BackendApp

