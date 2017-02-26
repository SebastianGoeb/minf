#!/bin/bash

# Utils
function echobold()
{
	echo -e "\033[1m$1\033[0m"
}

# Find base dir
dir=$(cd "$(dirname "$0")"; pwd)

# Clone or clean floodlight
if [[ ! -d floodlight ]]; then
	echobold "Cloning fresh floodlight repo"
	cd $dir
    rm -rf floodlight
    git clone git@github.com:floodlight/floodlight.git floodlight
else
	echobold "Cleaning floodlight directory"
	cd "$dir/floodlight"
	git reset --hard
	git clean -fd
fi

# Checkout consistent version
# TODO tested to work with this version of Floodlight. Use release version of Floodlight when available
echobold "\nChecking out a commit that is known to work"
cd "$dir/floodlight"
git checkout bdddcb4810c04a3eef77c54567ee6728d1674162

# Init submodules
echobold "\n Initializing git submodules"
git submodule update --init

# Create symlinks to custom controller code
echobold "\nCreating symlinks to custom controller code"
paths=(
	"src/main/resources/floodlightloadbalancer.properties"
	"src/main/resources/META-INF/services/net.floodlightcontroller.core.module.IFloodlightModule"
	"src/main/java/net/floodlightcontroller/proactiveloadbalancer"
	"src/test/java/net/floodlightcontroller/proactiveloadbalancer"
)
for path in ${paths[@]}; do
	echo "./$path -> $dir/controller/$path"
	[ -f $path ] && rm $path
	[ -d $path ] && rm -rf $path
	ln -s "$dir/controller/$path" $path
done

# Setup eclipse project
echobold "\nCreating eclipse project"
ant eclipse
cat >.project <<EOF
<?xml version="1.0" encoding="UTF-8"?>
<projectDescription>
	<name>floodlight</name>
	<comment></comment>
	<projects>
	</projects>
	<buildSpec>
		<buildCommand>
			<name>org.eclipse.jdt.core.javabuilder</name>
			<arguments>
			</arguments>
		</buildCommand>
	</buildSpec>
	<natures>
		<nature>org.eclipse.jdt.core.javanature</nature>
	</natures>
	<linkedResources>
		<link>
			<name>src/main/resources/floodlightloadbalancer.properties</name>
			<type>1</type>
			<location>$dir/controller/src/main/resources/floodlightloadbalancer.properties</location>
		</link>
		<link>
			<name>src/main/resources/META-INF/services/net.floodlightcontroller.core.module.IFloodlightModule</name>
			<type>1</type>
			<location>$dir/controller/src/main/resources/META-INF/services/net.floodlightcontroller.core.module.IFloodlightModule</location>
		</link>
		<link>
			<name>src/main/java/net/floodlightcontroller/proactiveloadbalancer</name>
			<type>2</type>
			<location>$dir/controller/src/main/java/net/floodlightcontroller/proactiveloadbalancer</location>
		</link>
	</linkedResources>
</projectDescription>
EOF
