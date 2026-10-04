$workspacePlugin = "c:\Users\Lelouch\Downloads\LIVE_VOD_ALVARADO2023_20260924\.agents\plugins\mrea-framework"
$globalPlugin = "C:\Users\Lelouch\.gemini\config\plugins\mrea-framework"

# Ensure directories
New-Item -ItemType Directory -Force -Path "$workspacePlugin\rules" | Out-Null
New-Item -ItemType Directory -Force -Path "$workspacePlugin\skills" | Out-Null
New-Item -ItemType Directory -Force -Path "$globalPlugin\rules" | Out-Null
New-Item -ItemType Directory -Force -Path "$globalPlugin\skills" | Out-Null

# Copy rules
Copy-Item -Force "c:\Users\Lelouch\Downloads\LIVE_VOD_ALVARADO2023_20260924\.agents\rules\*" "$workspacePlugin\rules\"
Copy-Item -Force "c:\Users\Lelouch\Downloads\LIVE_VOD_ALVARADO2023_20260924\.agents\rules\*" "$globalPlugin\rules\"

# Copy skills
Copy-Item -Recurse -Force "c:\Users\Lelouch\Downloads\LIVE_VOD_ALVARADO2023_20260924\.agents\skills\*" "$workspacePlugin\skills\"
Copy-Item -Recurse -Force "c:\Users\Lelouch\Downloads\LIVE_VOD_ALVARADO2023_20260924\.agents\skills\*" "$globalPlugin\skills\"

# Copy manifest
Copy-Item -Force "$workspacePlugin\plugin.json" "$globalPlugin\plugin.json"

Write-Output "MREA plugin successfully deployed locally and globally."
