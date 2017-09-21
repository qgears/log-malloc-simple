#include <stdio.h>
#include <stdlib.h>
int main ()
{
	printf("Hello World!\n");
	void * p=malloc(111);
	free(p);
}
