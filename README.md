SetupWizard
===========

Simple Device Provisioning Application

Build with Android Studio
-------------------------
SetupWizard needs access to system API, therefore it can't be built only using
the public SDK. You first need to generate the libraries with all the needed
classes. The application also needs elevated privileges, so you need to sign
it with the right key to update the one in the system partition. to do this:

- Place this directory anywhere in the Android source tree
- Generate a keystore and keystore.properties using `gen-keystore.sh`

